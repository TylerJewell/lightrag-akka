package io.akka.lightrag.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

/** SPEC-001 R7, R8 — what the high-level branch returns and in what order. */
public class GlobalBranchTest {

  @Test
  public void relationsKeepSearchOrder() {
    var backend = Fixtures.scripted();
    var result = GlobalBranch.run(backend.searchRelations("theme", 40), backend);

    // E-F outranks A-B in the index and stays ahead, even though A-B's endpoints have
    // the higher degree. Nothing re-sorts this branch.
    assertEquals(List.of("E|F", "A|B"), Fixtures.keys(result.relations()));
    for (var r : result.relations()) {
      assertNull(r.rank());
      assertEquals(false, r.fromLocalBranch());
    }
  }

  @Test
  public void entitiesAreEndpointsSourceThenTargetFirstSeen() {
    var backend = Fixtures.scripted();
    var result = GlobalBranch.run(backend.searchRelations("theme", 40), backend);

    assertEquals(List.of("E", "F", "A", "B"), Fixtures.names(result.entities()));
    for (var e : result.entities()) {
      assertNull(e.rank());
    }
  }

  @Test
  public void theIndexOrderSurvivesEvenWhenItDisagreesWithWeightAndDegree() {
    // A-B is the weaker match but sits on the heavier, better-connected edge. A branch
    // that re-sorted the way the local one does would put it first.
    var backend =
        Fixtures.scripted()
            .scriptRelationHits(
                List.of(
                    new Model.Hit(Fixtures.rel("A", "B"), 0.9, 200L),
                    new Model.Hit(Fixtures.rel("E", "F"), 0.8, 201L)));
    var result = GlobalBranch.run(backend.searchRelations("theme", 40), backend);

    assertEquals(List.of("A|B", "E|F"), Fixtures.keys(result.relations()));
  }

  @Test
  public void anEndpointSeenTwiceIsListedOnce() {
    var backend =
        Fixtures.scripted()
            .scriptRelationHits(
                List.of(
                    new Model.Hit(Fixtures.rel("A", "B"), 0.9, 200L),
                    new Model.Hit(Fixtures.rel("A", "C"), 0.8, 201L)));
    var result = GlobalBranch.run(backend.searchRelations("theme", 40), backend);

    assertEquals(List.of("A", "B", "C"), Fixtures.names(result.entities()));
  }
}
