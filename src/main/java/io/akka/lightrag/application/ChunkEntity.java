package io.akka.lightrag.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import io.akka.lightrag.domain.Model.Chunk;

/** One passage of source text, cited by entities and relations extracted from it. */
@Component(id = "chunk")
public class ChunkEntity extends KeyValueEntity<ChunkEntity.State> {

  public record State(Chunk chunk) {}

  @Override
  public State emptyState() {
    return new State(null);
  }

  public Effect<String> put(Chunk chunk) {
    return effects().updateState(new State(chunk)).thenReply("ok");
  }

  public ReadOnlyEffect<State> get() {
    return effects().reply(currentState());
  }
}
