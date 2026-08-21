package io.akka.lightrag.domain;

import io.akka.lightrag.domain.Model.Chunk;
import io.akka.lightrag.domain.Model.Edge;
import io.akka.lightrag.domain.Model.EdgeKey;
import io.akka.lightrag.domain.Model.Hit;
import io.akka.lightrag.domain.Model.Node;
import java.util.List;
import java.util.Optional;

/**
 * Everything the retrieval pipeline needs from storage, and nothing else.
 *
 * <p>Two implementations exist: one over Akka entities, one in memory. Keeping the
 * pipeline behind this interface is what lets the same code be driven from a unit test,
 * from a benchmark and from an HTTP request without changing.
 */
public interface Backend {

  /** Ranked entity ids for a keyword string. {@code topK} is a cap, not a promise. */
  List<Hit> searchEntities(String keywords, int topK);

  /** Ranked relation ids, each {@code Hit#id} being an {@link EdgeKey}'s string form. */
  List<Hit> searchRelations(String keywords, int topK);

  List<Hit> searchChunks(String query, int topK);

  Optional<Node> node(String entityName);

  /** The number of edges incident to a node, counting an edge once. */
  int degree(String entityName);

  /** Neighbouring entity names in store order, first sighting kept. */
  List<String> neighbours(String entityName);

  Optional<Edge> edge(EdgeKey key);

  Optional<Chunk> chunk(String chunkId);
}
