package io.akka.lightrag.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import io.akka.lightrag.domain.Model.Edge;

/** One relation of the knowledge graph, keyed by its sorted endpoint pair. */
@Component(id = "edge")
public class EdgeEntity extends KeyValueEntity<EdgeEntity.State> {

  public record State(Edge edge) {}

  @Override
  public State emptyState() {
    return new State(null);
  }

  public Effect<String> put(Edge edge) {
    return effects().updateState(new State(edge)).thenReply("ok");
  }

  public ReadOnlyEffect<State> get() {
    return effects().reply(currentState());
  }
}
