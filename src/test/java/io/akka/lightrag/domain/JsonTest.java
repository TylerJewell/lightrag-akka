package io.akka.lightrag.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The rendering is what the token budget is measured against, so it is pinned to the
 * source's output character for character.
 *
 * <p>The expected string below was printed by the source itself
 * ({@code lightrag-port/probes/probe_06.py}), not derived from a reading of Python's
 * documentation.
 */
public class JsonTest {

  @Test
  public void rendersARecordExactlyTheWayTheSourceDoes() {
    var rendered =
        Json.object(
            Json.fields(
                "entity", "A \"B\" \\ C",
                "type", "T\ttab",
                "description", "line1\nline2 café 中"));

    assertEquals(
        "{\"entity\": \"A \\\"B\\\" \\\\ C\", \"type\": \"T\\ttab\", "
            + "\"description\": \"line1\\nline2 café 中\"}",
        rendered);
  }

  @Test
  public void countsTheSameNumberOfTokensAsTheSourceDid() {
    var rendered =
        Json.object(
            Json.fields(
                "entity", "A \"B\" \\ C",
                "type", "T\ttab",
                "description", "line1\nline2 café 中"));

    assertEquals(32, Tokenizer.count(rendered));
  }

  @Test
  public void aControlCharacterWithNoShorthandIsEscapedAsFourHexDigits() {
    assertEquals(
        "{\"a\": \"x\\u0001y\"}", Json.object(Json.fields("a", "x" + (char) 1 + "y")));
  }
}
