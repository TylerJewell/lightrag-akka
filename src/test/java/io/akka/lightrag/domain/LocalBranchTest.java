package io.akka.lightrag.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.akka.lightrag.domain.Model.Hit;
import java.util.List;
import org.junit.jupiter.api.Test;

/** SPEC-001 R4, R5, R6 — what the low-level branch returns and in what order. */
public class LocalBranchTest {

  @Test
  public void entitiesKeepSearchOrderAndCarryDegreeRank() {
    var backend = Fixtures.scripted();
    var result = LocalBranch.run(backend.searchEntities("alpha", 40), backend);

    assertEquals(List.of("A", "C"), Fixtures.names(result.entities()));
    assertEquals(Integer.valueOf(2), result.entities().get(0).rank());
    assertEquals(Integer.valueOf(2), result.entities().get(1).rank());
  }

  @Test
  public void incidentEdgesAreDeduplicatedOnTheSortedPair() {
    var backend = Fixtures.scripted();
    var result = LocalBranch.run(backend.searchEntities("alpha", 40), backend);

    // A contributes A-B and A-C; C contributes A-C again and C-E. The shared edge
    // appears once, keyed on the sorted pair rather than on which entity found it.
    assertEquals(3, result.relations().size());
    assertEquals(List.of("A|C", "A|B", "C|E"), Fixtures.keys(result.relations()));
  }

  @Test
  public void relationsSortByRankThenWeightAndTiesKeepCollectionOrder() {
    var backend = Fixtures.scripted();
    var result = LocalBranch.run(backend.searchEntities("alpha", 40), backend);

    // Every edge here has rank 4 (degree(src) + degree(tgt) = 2 + 2), so weight decides:
    // A-C at 2.0 leads, and A-B and C-E both at 1.0 keep the order they were collected
    // in. A relation's local rank is a graph property, not a similarity.
    assertEquals(List.of("A|C", "A|B", "C|E"), Fixtures.keys(result.relations()));
    for (var r : result.relations()) {
      assertEquals(Integer.valueOf(4), r.rank());
    }
  }

  @Test
  public void relationsCarryNoCreatedAtAndAreMarkedLocal() {
    var backend = Fixtures.scripted();
    var result = LocalBranch.run(backend.searchEntities("alpha", 40), backend);

    for (var r : result.relations()) {
      assertNull(r.createdAt());
      assertEquals(true, r.fromLocalBranch());
    }
  }

  @Test
  public void anEntityMissingFromTheGraphIsDropped() {
    var backend = Fixtures.scripted().scriptEntityHits(List.of(new Hit("GHOST", 0.99, 1L)));
    var result = LocalBranch.run(backend.searchEntities("alpha", 40), backend);

    assertEquals(List.of(), Fixtures.names(result.entities()));
    assertEquals(List.of(), Fixtures.keys(result.relations()));
  }
}
