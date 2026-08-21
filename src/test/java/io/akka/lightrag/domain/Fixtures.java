package io.akka.lightrag.domain;

import io.akka.lightrag.application.InMemoryBackend;
import io.akka.lightrag.domain.Model.Chunk;
import io.akka.lightrag.domain.Model.Edge;
import io.akka.lightrag.domain.Model.EdgeKey;
import io.akka.lightrag.domain.Model.Hit;
import io.akka.lightrag.domain.Model.Node;
import io.akka.lightrag.domain.Model.RetrievedEntity;
import io.akka.lightrag.domain.Model.RetrievedRelation;
import java.util.List;

/**
 * The graph the source-side probes ran against, so a Java answer and a Python answer are
 * answers to the same question.
 *
 * <pre>
 *   A-B w1.0   A-C w2.0   B-D w5.0   C-E w1.0   E-F w3.0
 *   degrees: A2 B2 C2 D1 E2 F1
 * </pre>
 */
public final class Fixtures {

  private Fixtures() {}

  public static InMemoryBackend graph() {
    var b = new InMemoryBackend();
    for (String n : List.of("A", "B", "C", "D", "E", "F")) {
      b.addNode(new Node(n, "T", "desc " + n, "f.md", List.of("c" + n.toLowerCase())));
    }
    b.addEdge(edge("A", "B", 1.0));
    b.addEdge(edge("A", "C", 2.0));
    b.addEdge(edge("B", "D", 5.0));
    b.addEdge(edge("C", "E", 1.0));
    b.addEdge(edge("E", "F", 3.0));
    return b;
  }

  /** A relation index's record id: the sorted pair in the form storage uses. */
  public static String rel(String a, String b) {
    return EdgeKey.of(a, b).storageId();
  }

  public static Edge edge(String a, String b, double weight) {
    var key = EdgeKey.of(a, b);
    return new Edge(key, weight, a + "-" + b, "f.md", List.of("c" + a + b));
  }

  public static InMemoryBackend scripted() {
    return graph()
        .scriptEntityHits(List.of(new Hit("A", 0.9, 100L), new Hit("C", 0.8, 101L)))
        .scriptRelationHits(
            List.of(new Hit(rel("E", "F"), 0.9, 200L), new Hit(rel("A", "B"), 0.8, 201L)));
  }

  public static List<String> names(List<RetrievedEntity> entities) {
    return entities.stream().map(RetrievedEntity::entityName).toList();
  }

  public static List<String> keys(List<RetrievedRelation> relations) {
    return relations.stream().map(r -> r.key().toString()).toList();
  }

  public static List<String> chunkIds(List<Chunk> chunks) {
    return chunks.stream().map(Chunk::chunkId).toList();
  }
}
