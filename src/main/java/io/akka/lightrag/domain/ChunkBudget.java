package io.akka.lightrag.domain;

import io.akka.lightrag.domain.Model.Chunk;
import java.util.ArrayList;
import java.util.List;

/**
 * The final cut on the chunk list: a top-k prefix, then a token budget.
 *
 * <p>The token budget takes two stages, and the reason is a circularity rather than
 * caution. What a reader is sent is {@code {reference_id, content}} per line, but a
 * reference id cannot be assigned until the survivors are known, and the survivors are
 * what the budget decides. So the first stage counts {@code {content}} alone to get a
 * candidate prefix, and the second re-renders that exact candidate list through the real
 * renderer and shrinks it until the real text fits.
 */
public final class ChunkBudget {

  private ChunkBudget() {}

  public static List<Chunk> cut(List<Chunk> merged, int chunkTopK, int tokenBudget) {
    // Zero is this field's "unset", not a request for nothing: the source guards the cut
    // on chunkTopK being positive and elsewhere falls back to topK when it is zero.
    List<Chunk> kept =
        (chunkTopK > 0 && merged.size() > chunkTopK) ? merged.subList(0, chunkTopK) : merged;
    if (tokenBudget <= 0 || kept.isEmpty()) {
      return List.of();
    }

    var approximate = new ArrayList<String>(kept.size());
    for (var c : kept) {
      approximate.add(Json.object(Json.fields("content", c.content())));
    }
    int k = Tokenizer.prefixThatFits(approximate, "\n", tokenBudget);

    while (k > 0) {
      var candidate = kept.subList(0, k);
      if (Tokenizer.count(ReferenceList.render(ReferenceList.assign(candidate).chunks()))
          <= tokenBudget) {
        break;
      }
      k--;
    }
    return List.copyOf(kept.subList(0, k));
  }

  /** The first stage's rendering, exposed so a test can show it is the cheaper of the
   *  two and therefore that the second stage has something to catch. */
  public static String approximateRender(List<Chunk> chunks) {
    var lines = new ArrayList<String>(chunks.size());
    for (var c : chunks) {
      lines.add(Json.object(Json.fields("content", c.content())));
    }
    return String.join("\n", lines);
  }
}
