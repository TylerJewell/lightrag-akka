package io.akka.lightrag.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.akka.lightrag.domain.Model.EdgeKey;
import io.akka.lightrag.domain.Model.RetrievedEntity;
import io.akka.lightrag.domain.Model.RetrievedRelation;
import java.util.List;
import org.junit.jupiter.api.Test;

/** SPEC-001 R12, R13, R14 — the projection that reaches a reader, and where it is cut. */
public class TokenTruncationTest {

  private static RetrievedEntity entity(String name, String word) {
    return new RetrievedEntity(
        name, "T", (word + " ").repeat(40), name.toLowerCase() + ".md",
        List.of("c1"), 2, 100L);
  }

  private static final List<RetrievedEntity> ENTITIES =
      List.of(entity("A", "alpha"), entity("B", "bravo"), entity("C", "charlie"));

  @Test
  public void theProjectionDropsFilePathAndCreatedAt() {
    var one = new RetrievedEntity("A", "T", "a description", "a.md", List.of("c1"), 2, 100L);
    var out = TokenTruncation.entities(List.of(one), 100_000);

    // Three fields in this order, no more. The source removes file_path and created_at
    // before counting and keeps the removed form, so neither ever reaches a reader
    // through this path -- and the rendered text is what a token budget measures, so it
    // is asserted whole rather than field by field.
    assertEquals(
        "{\"entity\": \"A\", \"type\": \"T\", \"description\": \"a description\"}",
        TokenTruncation.render(out.context().get(0)));
  }

  @Test
  public void aRelationIsRenderedFromItsSortedPair() {
    var local = RetrievedRelation.local(EdgeKey.of("B", "A"), 1.0, "ab", "a.md", List.of("c1"), 4);
    var out = TokenTruncation.relations(List.of(local), 100_000);

    assertEquals("A", out.context().get(0).entity1());
    assertEquals("B", out.context().get(0).entity2());

    // The same edge found through the index is rendered in the direction the index
    // stored, which is not the sorted one.
    var global = RetrievedRelation.global("B", "A", 1.0, "ab", "a.md", List.of("c1"), 9L);
    var fromIndex = TokenTruncation.relations(List.of(global), 100_000);

    assertEquals("B", fromIndex.context().get(0).entity1());
    assertEquals("A", fromIndex.context().get(0).entity2());
  }

  @Test
  public void theCutFallsBetweenWholeRecordsAtTheirRunningTotal() {
    // The three rendered records cost 57, 58 and 97 tokens, so the running totals of the
    // block are 57, 115 and 212. One token short of a total keeps the record before it.
    assertEquals(List.of("A"), entityNames(TokenTruncation.entities(ENTITIES, 114)));
    assertEquals(List.of("A", "B"), entityNames(TokenTruncation.entities(ENTITIES, 115)));
    assertEquals(List.of("A", "B"), entityNames(TokenTruncation.entities(ENTITIES, 211)));
    assertEquals(List.of("A", "B", "C"), entityNames(TokenTruncation.entities(ENTITIES, 212)));
  }

  @Test
  public void theBlockCostsWhatItsRecordsCostBecauseTheSeparatorMergesIntoTheBrace() {
    // The budget is counted over the joined block, separators included. With this
    // rendering that turns out to cost nothing: a newline between a closing and an
    // opening brace merges into one token either way, so the running totals above are
    // exactly the record costs summed. Measured rather than assumed, because the rule
    // and its observable effect are two different things.
    var rendered =
        ENTITIES.stream()
            .map(e -> TokenTruncation.render(new Model.EntityContext(
                e.entityName(), e.entityType(), e.description())))
            .toList();
    int summed = rendered.stream().mapToInt(Tokenizer::count).sum();

    assertEquals(summed, Tokenizer.count(TokenTruncation.renderBlock(rendered)));
    assertEquals(212, summed);
  }

  @Test
  public void aZeroBudgetKeepsNothing() {
    var out = TokenTruncation.entities(ENTITIES, 0);

    assertEquals(List.of(), out.context());
    assertEquals(List.of(), out.survivors());
  }

  @Test
  public void survivorsFollowTheFusedOrder() {
    var out = TokenTruncation.entities(ENTITIES, 115);

    assertEquals(List.of("A", "B"), Fixtures.names(out.survivors()));
    assertEquals(entityNames(out), Fixtures.names(out.survivors()));
  }

  private static List<String> entityNames(TokenTruncation.Entities out) {
    return out.context().stream().map(Model.EntityContext::entity).toList();
  }
}
