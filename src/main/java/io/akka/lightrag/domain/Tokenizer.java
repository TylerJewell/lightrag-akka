package io.akka.lightrag.domain;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import java.util.List;

/**
 * Token counting on the same vocabulary the source uses.
 *
 * <p>The source defaults to tiktoken's encoding for {@code gpt-4o-mini}, which is
 * {@code o200k_base}. Byte-pair token counts are not proportional to length, so a
 * different vocabulary does not merely shift the budget — it reorders which records fit.
 */
public final class Tokenizer {

  private static final Encoding ENCODING =
      Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.O200K_BASE);

  private Tokenizer() {}

  public static int count(String text) {
    return ENCODING.countTokens(text);
  }

  /**
   * The largest {@code k} for which the first {@code k} renderings, joined by
   * {@code separator}, fit the budget.
   *
   * <p>Counted over the joined text, so the separators are inside the budget. A budget of
   * zero or less keeps nothing.
   *
   * <p>Each record is encoded once and the running sum gives a starting guess; the guess
   * is then confirmed against the joined text and walked in whichever direction that
   * requires. Byte-pair counts do not add up across a join — a separator can cost a token
   * or, where it merges with the characters either side, save one — so the sum decides
   * where to start looking and only the joined text decides the answer.
   */
  public static int prefixThatFits(List<String> rendered, String separator, int maxTokens) {
    if (maxTokens <= 0 || rendered.isEmpty()) {
      return 0;
    }

    int k = 0;
    long running = 0;
    var perItem = new int[rendered.size()];
    for (int i = 0; i < rendered.size(); i++) {
      perItem[i] = count(rendered.get(i));
      running += perItem[i];
      if (running > maxTokens) {
        break;
      }
      k++;
    }

    while (k > 0 && count(String.join(separator, rendered.subList(0, k))) > maxTokens) {
      k--;
    }
    while (k < rendered.size()
        && count(String.join(separator, rendered.subList(0, k + 1))) <= maxTokens) {
      k++;
    }
    return k;
  }
}
