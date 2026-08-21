package io.akka.lightrag.application;

import akka.javasdk.client.ComponentClient;
import io.akka.lightrag.domain.Backend;
import io.akka.lightrag.domain.Model.Chunk;
import io.akka.lightrag.domain.Model.Edge;
import io.akka.lightrag.domain.Model.EdgeKey;
import io.akka.lightrag.domain.Model.Hit;
import io.akka.lightrag.domain.Model.Node;
import io.akka.lightrag.domain.VectorSearch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The retrieval pipeline's view of the graph and the indexes, over Akka components.
 *
 * <p>One instance serves one query and is thrown away, which is what makes the caches
 * below safe: they are per-request memos on a single-threaded read path, not shared
 * state. They are also not optional. The local branch asks for a node's degree and its
 * neighbours separately, and then for the degree of both endpoints of every edge it
 * collected — over a forty-entity result that is several hundred requests for a few dozen
 * distinct nodes, each one a round trip to the same entity.
 */
public final class AkkaBackend implements Backend {

  /** How many shards each of the three indexes is spread over. Capacity, not behaviour:
   *  every shard is scanned, so the answer does not depend on this number. */
  public static final int SHARDS = 8;

  public static final String ENTITY_INDEX = "entity";
  public static final String RELATION_INDEX = "relation";
  public static final String CHUNK_INDEX = "chunk";

  private final ComponentClient componentClient;
  private final Map<String, NodeEntity.State> nodes = new HashMap<>();
  private final Map<EdgeKey, Optional<Edge>> edges = new HashMap<>();
  private final Map<String, Optional<Chunk>> chunks = new HashMap<>();

  public AkkaBackend(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public static String shardId(String index, String recordId) {
    return index + "-" + Math.floorMod(recordId.hashCode(), SHARDS);
  }

  @Override
  public List<Hit> searchEntities(String keywords, int topK) {
    return search(ENTITY_INDEX, keywords, topK);
  }

  @Override
  public List<Hit> searchRelations(String keywords, int topK) {
    return search(RELATION_INDEX, keywords, topK);
  }

  @Override
  public List<Hit> searchChunks(String query, int topK) {
    return search(CHUNK_INDEX, query, topK);
  }

  private List<Hit> search(String index, String text, int topK) {
    var command =
        new VectorShardEntity.SearchCommand(
            Embedder.embed(text), topK, VectorSearch.DEFAULT_COSINE_THRESHOLD);
    var perShard = new ArrayList<List<Hit>>(SHARDS);
    for (int shard = 0; shard < SHARDS; shard++) {
      perShard.add(
          componentClient
              .forKeyValueEntity(index + "-" + shard)
              .method(VectorShardEntity::search)
              .invoke(command));
    }
    return VectorSearch.merge(perShard, topK, VectorSearch.DEFAULT_COSINE_THRESHOLD);
  }

  @Override
  public Optional<Node> node(String entityName) {
    return Optional.ofNullable(nodeState(entityName).node());
  }

  @Override
  public int degree(String entityName) {
    return nodeState(entityName).neighbours().size();
  }

  @Override
  public List<String> neighbours(String entityName) {
    return nodeState(entityName).neighbours();
  }

  private NodeEntity.State nodeState(String entityName) {
    return nodes.computeIfAbsent(
        entityName,
        name ->
            componentClient
                .forKeyValueEntity(Ids.node(name))
                .method(NodeEntity::get)
                .invoke());
  }

  @Override
  public Optional<Edge> edge(EdgeKey key) {
    return edges.computeIfAbsent(
        key,
        k ->
            Optional.ofNullable(
                componentClient
                    .forKeyValueEntity(Ids.edge(k))
                    .method(EdgeEntity::get)
                    .invoke()
                    .edge()));
  }

  @Override
  public Optional<Chunk> chunk(String chunkId) {
    return chunks.computeIfAbsent(
        chunkId,
        id ->
            Optional.ofNullable(
                componentClient
                    .forKeyValueEntity(Ids.chunk(id))
                    .method(ChunkEntity::get)
                    .invoke()
                    .chunk()));
  }
}
