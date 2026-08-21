package io.akka.lightrag.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import io.akka.lightrag.domain.Model.Hit;
import io.akka.lightrag.domain.VectorSearch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * One shard of a vector index: some ids, their embeddings, and an exact scan over them.
 *
 * <p>The scan runs inside the entity and replies with a top-k, so the reply size depends
 * on what was asked for rather than on how much the shard holds. Every shard is scanned
 * in full and returns its own top-k, so merging the shards yields the ranking one
 * unsharded scan would (see {@code VectorSearch#merge}); sharding is a capacity decision,
 * not a behavioural one.
 *
 * <p><b>The cap is not decoration.</b> One key-value entity holds about 88 embeddings of
 * 1,536 dimensions, and a write past that is not refused in a way a caller can act on:
 * the runtime logs a storage-limit error and the caller gets a ten-second timeout naming
 * no size. So the shard refuses first, with an error that names the cap and both counts.
 */
@Component(id = "vector-shard")
public class VectorShardEntity extends KeyValueEntity<VectorShardEntity.State> {

  /** Below the measured ceiling with room for the id strings stored alongside. */
  public static final int MAX_VECTORS_PER_SHARD = 64;

  public record Vector(String id, float[] embedding, Long createdAt) {}

  public record State(List<Vector> vectors) {}

  public record Upsert(List<Vector> vectors) {}

  public record SearchCommand(float[] query, int topK, double threshold) {}

  @Override
  public State emptyState() {
    return new State(List.of());
  }

  public Effect<Integer> upsert(Upsert cmd) {
    var replaced = new HashSet<String>();
    for (var v : cmd.vectors()) {
      replaced.add(v.id());
    }
    var next = new ArrayList<Vector>();
    for (var existing : currentState().vectors()) {
      if (!replaced.contains(existing.id())) {
        next.add(existing);
      }
    }
    next.addAll(cmd.vectors());
    if (next.size() > MAX_VECTORS_PER_SHARD) {
      return effects()
          .error(
              "vector shard ["
                  + commandContext().entityId()
                  + "] holds at most "
                  + MAX_VECTORS_PER_SHARD
                  + " vectors; this write would leave "
                  + next.size());
    }
    return effects().updateState(new State(next)).thenReply(next.size());
  }

  public ReadOnlyEffect<List<Hit>> search(SearchCommand cmd) {
    var hits = new ArrayList<Hit>(currentState().vectors().size());
    for (var v : currentState().vectors()) {
      hits.add(new Hit(v.id(), VectorSearch.cosine(cmd.query(), v.embedding()), v.createdAt()));
    }
    return effects().reply(VectorSearch.rank(hits, cmd.topK(), cmd.threshold()));
  }

  public ReadOnlyEffect<Integer> size() {
    return effects().reply(currentState().vectors().size());
  }
}
