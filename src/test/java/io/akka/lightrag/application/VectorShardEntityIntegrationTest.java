package io.akka.lightrag.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akka.javasdk.testkit.TestKitSupport;
import io.akka.lightrag.domain.Model.Hit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * SPEC-001 R23, R24 — the shard, against a running runtime.
 *
 * <p>This starts the Akka test runtime, which is why it is slower than the domain tests.
 */
public class VectorShardEntityIntegrationTest extends TestKitSupport {

  private static VectorShardEntity.Vector vector(String id, float... values) {
    return new VectorShardEntity.Vector(id, values, null);
  }

  private static List<VectorShardEntity.Vector> many(int n) {
    var out = new ArrayList<VectorShardEntity.Vector>(n);
    for (int i = 0; i < n; i++) {
      out.add(vector("v" + i, 1.0f, i, 0.5f));
    }
    return out;
  }

  @Test
  public void aShardRefusesAWritePastItsCapAndNamesBothCounts() {
    componentClient
        .forKeyValueEntity("cap-test")
        .method(VectorShardEntity::upsert)
        .invoke(new VectorShardEntity.Upsert(many(VectorShardEntity.MAX_VECTORS_PER_SHARD)));

    var refusal =
        assertThrows(
            RuntimeException.class,
            () ->
                componentClient
                    .forKeyValueEntity("cap-test")
                    .method(VectorShardEntity::upsert)
                    .invoke(new VectorShardEntity.Upsert(List.of(vector("one-too-many", 1f)))));

    // The runtime's own state ceiling surfaces as a timeout naming no size, so the
    // refusal has to name the cap itself or a caller cannot tell why the write failed.
    var message = String.valueOf(refusal.getMessage());
    assertTrue(
        message.contains(String.valueOf(VectorShardEntity.MAX_VECTORS_PER_SHARD)),
        "the refusal must name the cap, was: " + message);
    assertTrue(
        message.contains(String.valueOf(VectorShardEntity.MAX_VECTORS_PER_SHARD + 1)),
        "the refusal must name what the write would have left, was: " + message);

    assertEquals(
        Integer.valueOf(VectorShardEntity.MAX_VECTORS_PER_SHARD),
        componentClient.forKeyValueEntity("cap-test").method(VectorShardEntity::size).invoke());
  }

  @Test
  public void aRewriteOfTheSameIdDoesNotGrowTheShard() {
    var full = many(VectorShardEntity.MAX_VECTORS_PER_SHARD);
    componentClient
        .forKeyValueEntity("rewrite-test")
        .method(VectorShardEntity::upsert)
        .invoke(new VectorShardEntity.Upsert(full));

    var size =
        componentClient
            .forKeyValueEntity("rewrite-test")
            .method(VectorShardEntity::upsert)
            .invoke(new VectorShardEntity.Upsert(List.of(vector("v0", 9f, 9f, 9f))));

    assertEquals(Integer.valueOf(VectorShardEntity.MAX_VECTORS_PER_SHARD), size);
  }

  @Test
  public void theShardScansAndRepliesWithATopK() {
    componentClient
        .forKeyValueEntity("scan-test")
        .method(VectorShardEntity::upsert)
        .invoke(
            new VectorShardEntity.Upsert(
                List.of(
                    vector("near", 1.0f, 0.0f),
                    vector("far", 0.0f, 1.0f),
                    vector("middle", 1.0f, 1.0f))));

    List<Hit> hits =
        componentClient
            .forKeyValueEntity("scan-test")
            .method(VectorShardEntity::search)
            .invoke(new VectorShardEntity.SearchCommand(new float[] {1.0f, 0.0f}, 2, 0.2));

    assertEquals(List.of("near", "middle"), hits.stream().map(Hit::id).toList());
  }
}
