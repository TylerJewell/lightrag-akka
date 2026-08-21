package io.akka.lightrag.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.akka.lightrag.domain.Model.Chunk;
import io.akka.lightrag.domain.Model.EdgeKey;
import io.akka.lightrag.domain.Model.Hit;
import io.akka.lightrag.domain.Model.Mode;
import io.akka.lightrag.domain.Model.Node;
import io.akka.lightrag.domain.Model.QuerySpec;
import io.akka.lightrag.domain.RetrievalPipeline.Query;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** SPEC-001 R1, R2, R3 — which branch a mode actually runs. */
public class DualLevelRetrievalTest {

  /** Counts what was asked of the index, so "no results" and "never asked" stay apart. */
  private static final class Counting implements Backend {
    private final Backend inner;
    final List<String> calls = new ArrayList<>();

    Counting(Backend inner) {
      this.inner = inner;
    }

    @Override
    public List<Hit> searchEntities(String keywords, int topK) {
      calls.add("entities:" + keywords);
      return inner.searchEntities(keywords, topK);
    }

    @Override
    public List<Hit> searchRelations(String keywords, int topK) {
      calls.add("relations:" + keywords);
      return inner.searchRelations(keywords, topK);
    }

    @Override
    public List<Hit> searchChunks(String query, int topK) {
      calls.add("chunks:" + query + ":" + topK);
      return inner.searchChunks(query, topK);
    }

    @Override
    public Optional<Node> node(String entityName) {
      return inner.node(entityName);
    }

    @Override
    public int degree(String entityName) {
      return inner.degree(entityName);
    }

    @Override
    public List<String> neighbours(String entityName) {
      return inner.neighbours(entityName);
    }

    @Override
    public Optional<Model.Edge> edge(EdgeKey key) {
      return inner.edge(key);
    }

    @Override
    public Optional<Chunk> chunk(String chunkId) {
      return inner.chunk(chunkId);
    }
  }

  private static Counting backend() {
    return new Counting(
        Fixtures.scripted()
            .scriptChunkHits(List.of(new Hit("cv1", 0.9, 1L), new Hit("cv2", 0.8, 2L)))
            .addChunk(new Chunk("cv1", "vector one", "v.md"))
            .addChunk(new Chunk("cv2", "vector two", "v.md")));
  }

  @Test
  public void localModeWithNoLowLevelKeywordsRetrievesGlobally() {
    var b = backend();
    var search =
        RetrievalPipeline.search(
            new Query("q", List.of(), List.of("theme")), QuerySpec.defaults(Mode.LOCAL), b);

    // Mode is not a branch selector on its own: the local branch is guarded on the
    // low-level keyword set being non-empty, and the fall-through is the hybrid branch.
    assertEquals(List.of("E", "F", "A", "B"), Fixtures.names(search.entities()));
    assertEquals(List.of("E|F", "A|B"), Fixtures.keys(search.relations()));
  }

  @Test
  public void globalModeWithNoHighLevelKeywordsRetrievesLocally() {
    var b = backend();
    var search =
        RetrievalPipeline.search(
            new Query("q", List.of("alpha"), List.of()), QuerySpec.defaults(Mode.GLOBAL), b);

    assertEquals(List.of("A", "C"), Fixtures.names(search.entities()));
    assertEquals(List.of("A|C", "A|B", "C|E"), Fixtures.keys(search.relations()));
  }

  @Test
  public void noKeywordsAtEitherLevelQueriesNoIndex() {
    var b = backend();
    var search =
        RetrievalPipeline.search(
            new Query("q", List.of(), List.of()), QuerySpec.defaults(Mode.HYBRID), b);

    assertEquals(List.of(), search.entities());
    assertEquals(List.of(), search.relations());
    assertEquals(List.of(), b.calls);
  }

  @Test
  public void onlyMixModeSearchesTheChunkIndex() {
    var mix = backend();
    RetrievalPipeline.search(
        new Query("q", List.of("alpha"), List.of("theme")), QuerySpec.defaults(Mode.MIX), mix);

    var hybrid = backend();
    var hybridSearch =
        RetrievalPipeline.search(
            new Query("q", List.of("alpha"), List.of("theme")),
            QuerySpec.defaults(Mode.HYBRID),
            hybrid);

    assertEquals(List.of("entities:alpha", "relations:theme", "chunks:q:20"), mix.calls);
    assertEquals(List.of("entities:alpha", "relations:theme"), hybrid.calls);
    assertEquals(List.of(), hybridSearch.vectorChunks());
  }

  @Test
  public void theChunkSearchFallsBackToTopKWhenChunkTopKIsUnset() {
    var b = backend();
    var spec = new QuerySpec(Mode.MIX, 40, 0, 6000, 8000, 12000, 5);
    RetrievalPipeline.search(new Query("q", List.of("alpha"), List.of("theme")), spec, b);

    assertEquals("chunks:q:40", b.calls.get(2));
  }

  @Test
  public void hybridModeFusesBothBranches() {
    var b = backend();
    var search =
        RetrievalPipeline.search(
            new Query("q", List.of("alpha"), List.of("theme")),
            QuerySpec.defaults(Mode.HYBRID),
            b);

    assertEquals(List.of("A", "E", "C", "F", "B"), Fixtures.names(search.entities()));
    assertEquals(List.of("A|C", "E|F", "A|B", "C|E"), Fixtures.keys(search.relations()));
  }
}
