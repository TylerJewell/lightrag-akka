package io.akka.lightrag.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.akka.lightrag.domain.Model.Hit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** SPEC-001 R23 — this port's own ranking rule, and the property that lets it shard. */
public class VectorIndexTest {

  @Test
  public void tiesBreakOnTheIdAscending() {
    var hits =
        List.of(
            new Hit("zulu", 0.75, null),
            new Hit("alpha", 0.75, null),
            new Hit("mike", 0.9, null),
            new Hit("bravo", 0.75, null));

    assertEquals(
        List.of("mike", "alpha", "bravo", "zulu"),
        VectorSearch.rank(hits, 10, 0.2).stream().map(Hit::id).toList());
  }

  @Test
  public void anythingAtOrBelowTheThresholdIsDropped() {
    var hits = List.of(new Hit("a", 0.2, null), new Hit("b", 0.2001, null));

    assertEquals(List.of("b"), VectorSearch.rank(hits, 10, 0.2).stream().map(Hit::id).toList());
  }

  @Test
  public void shardingDoesNotChangeTheRanking() {
    // Every shard is scanned in full and returns its own top k, so merging them gives
    // the ranking one unsharded scan would. This is what makes the shard count a
    // capacity decision rather than a behavioural one.
    var all = new ArrayList<Hit>();
    for (int i = 0; i < 60; i++) {
      all.add(new Hit("id-" + i, 0.3 + (i % 7) * 0.05, null));
    }
    var unsharded = VectorSearch.rank(all, 10, 0.2);

    var shards = new ArrayList<List<Hit>>();
    for (int s = 0; s < 4; s++) {
      var shard = new ArrayList<Hit>();
      for (int i = s; i < all.size(); i += 4) {
        shard.add(all.get(i));
      }
      shards.add(VectorSearch.rank(shard, 10, 0.2));
    }

    assertEquals(unsharded, VectorSearch.merge(shards, 10, 0.2));
  }

  @Test
  public void cosineOfAVectorWithItselfIsOne() {
    float[] v = {1.0f, 2.0f, 3.0f};

    assertEquals(1.0, VectorSearch.cosine(v, v), 1e-12);
  }

  @Test
  public void anEmptyVectorScoresZeroRatherThanFailing() {
    assertEquals(0.0, VectorSearch.cosine(new float[3], new float[] {1, 2, 3}), 0.0);
  }
}
