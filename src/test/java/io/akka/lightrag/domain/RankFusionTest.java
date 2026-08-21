package io.akka.lightrag.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.akka.lightrag.domain.Model.RetrievedEntity;
import io.akka.lightrag.domain.Model.RetrievedRelation;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * SPEC-001 R9, R10, R11 — the fusion.
 *
 * <p>There is no score in any of this. The source interleaves two lists and drops
 * repeats; position in the input lists is the whole of the ranking. A port that reached
 * for a reciprocal-rank fusion would produce a different order on every asymmetric query
 * while looking like a reasonable implementation of the same words.
 */
public class RankFusionTest {

  @Test
  public void entitiesFuseAsALocalFirstRoundRobin() {
    var backend = Fixtures.scripted();
    List<RetrievedEntity> local = LocalBranch.run(backend.searchEntities("a", 40), backend).entities();
    List<RetrievedEntity> global = GlobalBranch.run(backend.searchRelations("t", 40), backend).entities();

    var fused = RoundRobinFusion.fuse(List.of(local, global), RetrievedEntity::entityName);

    // local [A, C] and global [E, F, A, B] interleave to [A, E, C, F, B]: A takes its
    // local slot and is skipped when the global list offers it again.
    assertEquals(List.of("A", "E", "C", "F", "B"), Fixtures.names(fused));
  }

  @Test
  public void relationsFuseKeyedOnTheSortedPair() {
    var backend = Fixtures.scripted();
    List<RetrievedRelation> local = LocalBranch.run(backend.searchEntities("a", 40), backend).relations();
    List<RetrievedRelation> global = GlobalBranch.run(backend.searchRelations("t", 40), backend).relations();

    var fused = RoundRobinFusion.fuse(List.of(local, global), RetrievedRelation::key);

    assertEquals(List.of("A|C", "E|F", "A|B", "C|E"), Fixtures.keys(fused));
  }

  @Test
  public void aRelationFoundByBothBranchesKeepsItsLocalRecord() {
    var backend = Fixtures.scripted();
    List<RetrievedRelation> local = LocalBranch.run(backend.searchEntities("a", 40), backend).relations();
    List<RetrievedRelation> global = GlobalBranch.run(backend.searchRelations("t", 40), backend).relations();

    var fused = RoundRobinFusion.fuse(List.of(local, global), RetrievedRelation::key);
    var ab = fused.stream().filter(r -> r.key().toString().equals("A|B")).findFirst().orElseThrow();

    // A-B is in both lists. The surviving record is the local one, so it has a rank and
    // no createdAt -- the two branches do not produce interchangeable records.
    assertTrue(ab.fromLocalBranch());
    assertNotNull(ab.rank());
    assertNull(ab.createdAt());
  }

  @Test
  public void aShorterListDoesNotStopTheLongerOne() {
    var fused =
        RoundRobinFusion.fuse(
            List.of(List.of("a"), List.of("x", "y", "z")), s -> s);

    assertEquals(List.of("a", "x", "y", "z"), fused);
  }

  @Test
  public void firstPlacementWinsAcrossAndWithinLists() {
    var fused =
        RoundRobinFusion.fuse(
            List.of(List.of("a", "b", "a"), List.of("b", "c")), s -> s);

    assertEquals(List.of("a", "b", "c"), fused);
  }
}
