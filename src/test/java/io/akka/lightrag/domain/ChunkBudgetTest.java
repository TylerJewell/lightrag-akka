package io.akka.lightrag.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.akka.lightrag.domain.Model.Chunk;
import java.util.List;
import org.junit.jupiter.api.Test;

/** SPEC-001 R22 — what the chunk budget is counted against, and the two stages it takes. */
public class ChunkBudgetTest {

  private static Chunk chunk(String id, String path) {
    return new Chunk(id, "body of " + id, path);
  }

  private static final List<Chunk> TEN =
      List.of(chunk("cv1", "v.md"), chunk("c2", "2.md"), chunk("c8", "8.md"),
              chunk("cv2", "v.md"), chunk("c3", "3.md"), chunk("c9", "9.md"),
              chunk("c1", "1.md"), chunk("c5", "5.md"), chunk("c7", "7.md"),
              chunk("c4", "4.md"));

  @Test
  public void theBudgetIsCountedAgainstTheRenderedProjection() {
    // Not against the chunk records: what is measured is one JSON object per line of
    // {reference_id, content}, which is what a reader is sent.
    int rendered = Tokenizer.count(ReferenceList.render(ReferenceList.assign(TEN).chunks()));

    assertEquals(10, ChunkBudget.cut(TEN, 20, rendered).size());
    assertTrue(ChunkBudget.cut(TEN, 20, rendered - 1).size() < 10);
  }

  @Test
  public void theSecondStageShrinksAPrefixTheFirstStageThoughtWouldFit() {
    // The first stage counts {content} alone, because a reference id cannot be known
    // until the survivors are. The rendered form is strictly longer, so a budget between
    // the two counts must be caught by the re-render rather than by the estimate.
    var assigned = ReferenceList.assign(TEN);
    int rendered = Tokenizer.count(ReferenceList.render(assigned.chunks()));
    int approx = Tokenizer.count(ChunkBudget.approximateRender(TEN));

    assertTrue(approx < rendered, "the estimate must be the cheaper of the two");
    assertTrue(ChunkBudget.cut(TEN, 20, approx).size() < 10);
  }

  @Test
  public void aZeroChunkBudgetKeepsNothing() {
    assertEquals(List.of(), ChunkBudget.cut(TEN, 20, 0));
  }

  @Test
  public void aTopKOfZeroAppliesNoCutAtAll() {
    // Zero is the source's "unset" for this field, not a request for nothing: the cut is
    // guarded by `chunk_top_k > 0`, and elsewhere a zero falls back to `top_k`.
    assertEquals(10, ChunkBudget.cut(TEN, 0, 100_000).size());
  }
}
