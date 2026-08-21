package io.akka.lightrag.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.akka.lightrag.domain.Model.EdgeKey;
import io.akka.lightrag.domain.Model.RetrievedEntity;
import io.akka.lightrag.domain.Model.RetrievedRelation;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** SPEC-001 R15, R16, R17, R18 — which chunks each entity and relation contributes. */
public class ChunkSelectionTest {

  private static RetrievedEntity entity(String name, String... chunkIds) {
    return new RetrievedEntity(name, "T", "d", "f.md", List.of(chunkIds), 1, null);
  }

  private static final List<RetrievedEntity> ENTITIES =
      List.of(
          entity("A", "c1", "c2", "c3", "c4"),
          entity("B", "c2", "c5"),
          entity("C", "c3", "c7"));

  @Test
  public void aSharedChunkGoesToTheFirstEntityAndPromotesWithinIt() {
    var out = ChunkSelection.fromEntities(ENTITIES, 3);

    // c2 is named by A and B, c3 by A and C. Both stay with A, and both are counted
    // twice -- which moves them ahead of A's own c1 and c4 inside A's list.
    assertEquals(List.of("c2", "c3", "c1", "c5", "c7", "c4"), out.chunkIds());
    assertEquals(Integer.valueOf(2), out.occurrences().get("c2"));
    assertEquals(Integer.valueOf(2), out.occurrences().get("c3"));
    assertEquals(Integer.valueOf(1), out.occurrences().get("c5"));
  }

  @Test
  public void quotasAreLinearAndUnfilledQuotaIsHandedBack() {
    // Quotas over three owners at 3 are 3, 2, 1. The first owner can only fill two of
    // its three, so the spare is pooled and handed back -- to the first owner with
    // anything left, which is the second. A flat quota of 3 each would have taken every
    // chunk the last two owners hold.
    var out =
        ChunkSelection.weightedPolling(
            List.of(
                List.of("a1", "a2"),
                List.of("b1", "b2", "b3"),
                List.of("cc1", "cc2", "cc3")),
            3, 1);

    assertEquals(List.of("a1", "a2", "b1", "b2", "cc1", "b3"), out);
  }

  @Test
  public void oneOwnerTakesItsQuotaWithNoSecondPass() {
    var out = ChunkSelection.weightedPolling(List.of(List.of("a1", "a2", "a3", "a4")), 3, 1);

    assertEquals(List.of("a1", "a2", "a3"), out);
  }

  @Test
  public void relatedChunkNumberIsTheTopQuotaNotATotal() {
    // Raising it from 3 to 5 pulls two more chunks out of the first entity and changes
    // nothing else -- it is not a cap on the result.
    var out = ChunkSelection.fromEntities(ENTITIES, 5);

    assertEquals(List.of("c2", "c3", "c1", "c4", "c5", "c7"), out.chunkIds());
  }

  @Test
  public void anEmptiedEntityStillCountsTowardsTheQuotaSplit() {
    // B names only a chunk A already named, so B ends the pass with nothing to give --
    // and still shortens the gradient, because the entity pass counts owners before the
    // deduplication rather than after. Quotas over three owners are 3, 2, 1: A fills two
    // of three, B none of two, C one of one, and the five unfilled go back to C, the
    // only owner with anything left. The relation pass, which drops an emptied owner,
    // would have stopped at cc4.
    var out =
        ChunkSelection.fromEntities(
            List.of(
                entity("A", "c1", "c2"),
                entity("B", "c2"),
                entity("C", "cc3", "cc4", "cc5", "cc6")),
            3);

    assertEquals(List.of("c2", "c1", "cc3", "cc4", "cc5", "cc6"), out.chunkIds());
  }

  @Test
  public void relationChunksExcludeWhatTheEntityPassTook() {
    var relations =
        List.of(
            RetrievedRelation.local(
                EdgeKey.of("A", "B"), 1.0, "ab", "a.md", List.of("c8", "c2"), 4),
            RetrievedRelation.global("C", "E", 2.0, "ce", "c.md", List.of("c9"), 200L));
    var taken = Set.of("c2", "c3", "c1", "c5", "c7", "c4");

    var out = ChunkSelection.fromRelations(relations, taken, 3);

    assertEquals(List.of("c8", "c9"), out.chunkIds());
  }

  @Test
  public void aRelationLeftWithNothingDropsOutOfTheQuotaSplit() {
    var relations =
        List.of(
            RetrievedRelation.local(EdgeKey.of("A", "B"), 1.0, "ab", "a.md", List.of("c2"), 4),
            RetrievedRelation.global("C", "E", 2.0, "ce", "c.md", List.of("c9", "c10"), 200L));

    // With A-B's only chunk already taken, the quotas are computed over one relation,
    // not two -- so C-E gets the top quota rather than the bottom one.
    var out = ChunkSelection.fromRelations(relations, Set.of("c2"), 2);

    assertEquals(List.of("c9", "c10"), out.chunkIds());
  }
}
