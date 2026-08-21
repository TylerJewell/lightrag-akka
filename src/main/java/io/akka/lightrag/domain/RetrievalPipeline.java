package io.akka.lightrag.domain;

import io.akka.lightrag.domain.Model.Chunk;
import io.akka.lightrag.domain.Model.Hit;
import io.akka.lightrag.domain.Model.Mode;
import io.akka.lightrag.domain.Model.QuerySpec;
import io.akka.lightrag.domain.Model.RetrievalResult;
import io.akka.lightrag.domain.Model.RetrievedEntity;
import io.akka.lightrag.domain.Model.RetrievedRelation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The whole of dual-level retrieval, in the four stages the source runs it in: search,
 * truncate, gather chunks, cut.
 *
 * <p>The branch is chosen from the mode <em>and</em> the keyword sets together. Asking for
 * the low-level branch with no low-level keywords falls through to the both-branches case,
 * which then runs the high-level branch alone — so a query can be answered from the
 * opposite index to the one its mode names. That is reproduced deliberately (SPEC-001
 * §4.3): it is reachable on ordinary input, and a port that quietly returned nothing here
 * would differ from the original on real queries while claiming to match.
 */
public final class RetrievalPipeline {

  private RetrievalPipeline() {}

  /** @param llKeywords the specific terms; @param hlKeywords the broad ones. Both are
   *  produced by a model in the source and supplied by the caller here. */
  public record Query(String text, List<String> llKeywords, List<String> hlKeywords) {

    String ll() {
      return String.join(", ", llKeywords);
    }

    String hl() {
      return String.join(", ", hlKeywords);
    }
  }

  public record Search(
      List<RetrievedEntity> entities,
      List<RetrievedRelation> relations,
      List<Chunk> vectorChunks) {}

  public static Search search(Query query, QuerySpec spec, Backend backend) {
    String ll = query.ll();
    String hl = query.hl();
    boolean runLocal;
    boolean runGlobal;
    boolean runChunks = false;

    if (spec.mode() == Mode.LOCAL && !ll.isEmpty()) {
      runLocal = true;
      runGlobal = false;
    } else if (spec.mode() == Mode.GLOBAL && !hl.isEmpty()) {
      runLocal = false;
      runGlobal = true;
    } else {
      runLocal = !ll.isEmpty();
      runGlobal = !hl.isEmpty();
      runChunks = spec.mode() == Mode.MIX;
    }

    var localResult =
        runLocal
            ? LocalBranch.run(backend.searchEntities(ll, spec.topK()), backend)
            : new LocalBranch.Result(List.of(), List.of());
    var globalResult =
        runGlobal
            ? GlobalBranch.run(backend.searchRelations(hl, spec.topK()), backend)
            : new GlobalBranch.Result(List.of(), List.of());

    var vectorChunks = new ArrayList<Chunk>();
    if (runChunks) {
      int chunkSearchTopK = spec.chunkTopK() > 0 ? spec.chunkTopK() : spec.topK();
      for (Hit hit : backend.searchChunks(query.text(), chunkSearchTopK)) {
        backend.chunk(hit.id()).ifPresent(vectorChunks::add);
      }
    }

    return new Search(
        RoundRobinFusion.fuse(
            List.of(localResult.entities(), globalResult.entities()),
            RetrievedEntity::entityName),
        RoundRobinFusion.fuse(
            List.of(localResult.relations(), globalResult.relations()),
            RetrievedRelation::key),
        List.copyOf(vectorChunks));
  }

  public static RetrievalResult retrieve(Query query, QuerySpec spec, Backend backend) {
    var search = search(query, spec, backend);

    var entities = TokenTruncation.entities(search.entities(), spec.maxEntityTokens());
    var relations = TokenTruncation.relations(search.relations(), spec.maxRelationTokens());

    var fromEntities =
        ChunkSelection.fromEntities(entities.survivors(), spec.relatedChunkNumber());
    var takenByEntities = new LinkedHashSet<>(fromEntities.chunkIds());
    var fromRelations =
        ChunkSelection.fromRelations(
            relations.survivors(), takenByEntities, spec.relatedChunkNumber());

    var merged =
        ChunkFusion.fuse(
            search.vectorChunks(),
            resolve(fromEntities.chunkIds(), backend),
            resolve(fromRelations.chunkIds(), backend));

    var kept = ChunkBudget.cut(merged, spec.chunkTopK(), spec.chunkTokenBudget());
    var assigned = ReferenceList.assign(kept);

    return new RetrievalResult(
        entities.context(), relations.context(), assigned.chunks(), assigned.references());
  }

  private static List<Chunk> resolve(List<String> chunkIds, Backend backend) {
    var out = new ArrayList<Chunk>(chunkIds.size());
    for (String id : chunkIds) {
      backend.chunk(id).ifPresent(out::add);
    }
    return out;
  }
}
