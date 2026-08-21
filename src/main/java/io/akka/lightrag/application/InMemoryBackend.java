package io.akka.lightrag.application;

import io.akka.lightrag.domain.Backend;
import io.akka.lightrag.domain.Model.Chunk;
import io.akka.lightrag.domain.Model.Edge;
import io.akka.lightrag.domain.Model.EdgeKey;
import io.akka.lightrag.domain.Model.Hit;
import io.akka.lightrag.domain.Model.Node;
import io.akka.lightrag.domain.VectorSearch;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A whole index in one process: the graph, the chunks, and either scripted hit lists or a
 * real cosine scan.
 *
 * <p>Scripted hits exist so a test can fix what the vector search returns and vary only
 * what the pipeline does with it — the same separation the source-side probes make, where
 * the ranking lives in a package outside the system under test.
 */
public final class InMemoryBackend implements Backend {

  private final Map<String, Node> nodes = new LinkedHashMap<>();
  private final Map<EdgeKey, Edge> edges = new LinkedHashMap<>();
  private final Map<String, Chunk> chunks = new LinkedHashMap<>();
  private final Map<String, LinkedHashSet<String>> adjacency = new LinkedHashMap<>();

  private final Map<String, float[]> entityVectors = new LinkedHashMap<>();
  private final Map<String, float[]> relationVectors = new LinkedHashMap<>();
  private final Map<String, float[]> chunkVectors = new LinkedHashMap<>();

  private List<Hit> scriptedEntityHits;
  private List<Hit> scriptedRelationHits;
  private List<Hit> scriptedChunkHits;

  public InMemoryBackend addNode(Node node) {
    nodes.put(node.entityName(), node);
    adjacency.computeIfAbsent(node.entityName(), k -> new LinkedHashSet<>());
    entityVectors.put(node.entityName(), Embedder.embed(node.entityName() + " " + node.description()));
    return this;
  }

  public InMemoryBackend addEdge(Edge edge) {
    edges.put(edge.key(), edge);
    adjacency.computeIfAbsent(edge.key().a(), k -> new LinkedHashSet<>()).add(edge.key().b());
    adjacency.computeIfAbsent(edge.key().b(), k -> new LinkedHashSet<>()).add(edge.key().a());
    relationVectors.put(edge.key().storageId(), Embedder.embed(edge.description()));
    return this;
  }

  public InMemoryBackend addChunk(Chunk chunk) {
    chunks.put(chunk.chunkId(), chunk);
    chunkVectors.put(chunk.chunkId(), Embedder.embed(chunk.content()));
    return this;
  }

  /** Fixes what the entity index returns, so a test can vary only the pipeline. */
  public InMemoryBackend scriptEntityHits(List<Hit> hits) {
    this.scriptedEntityHits = List.copyOf(hits);
    return this;
  }

  public InMemoryBackend scriptRelationHits(List<Hit> hits) {
    this.scriptedRelationHits = List.copyOf(hits);
    return this;
  }

  public InMemoryBackend scriptChunkHits(List<Hit> hits) {
    this.scriptedChunkHits = List.copyOf(hits);
    return this;
  }

  @Override
  public List<Hit> searchEntities(String keywords, int topK) {
    return scriptedEntityHits != null
        ? cap(scriptedEntityHits, topK)
        : scan(entityVectors, keywords, topK);
  }

  @Override
  public List<Hit> searchRelations(String keywords, int topK) {
    return scriptedRelationHits != null
        ? cap(scriptedRelationHits, topK)
        : scan(relationVectors, keywords, topK);
  }

  @Override
  public List<Hit> searchChunks(String query, int topK) {
    return scriptedChunkHits != null
        ? cap(scriptedChunkHits, topK)
        : scan(chunkVectors, query, topK);
  }

  private static List<Hit> cap(List<Hit> hits, int topK) {
    return hits.size() <= topK ? hits : List.copyOf(hits.subList(0, topK));
  }

  private static List<Hit> scan(Map<String, float[]> vectors, String text, int topK) {
    float[] q = Embedder.embed(text);
    var hits = new ArrayList<Hit>(vectors.size());
    vectors.forEach((id, v) -> hits.add(new Hit(id, VectorSearch.cosine(q, v), null)));
    return VectorSearch.rank(hits, topK, VectorSearch.DEFAULT_COSINE_THRESHOLD);
  }

  @Override
  public Optional<Node> node(String entityName) {
    return Optional.ofNullable(nodes.get(entityName));
  }

  @Override
  public int degree(String entityName) {
    return adjacency.getOrDefault(entityName, new LinkedHashSet<>()).size();
  }

  @Override
  public List<String> neighbours(String entityName) {
    return List.copyOf(adjacency.getOrDefault(entityName, new LinkedHashSet<>()));
  }

  @Override
  public Optional<Edge> edge(EdgeKey key) {
    return Optional.ofNullable(edges.get(key));
  }

  @Override
  public Optional<Chunk> chunk(String chunkId) {
    return Optional.ofNullable(chunks.get(chunkId));
  }
}
