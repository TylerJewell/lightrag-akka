package io.akka.lightrag.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One-line JSON objects rendered byte for byte the way the source renders them.
 *
 * <p>The source counts a token budget against {@code json.dumps(record, ensure_ascii=False)}
 * joined by newlines, so the rendering is not a display choice — it is the thing being
 * measured. Python's defaults put a space after every colon and every comma, leave
 * non-ASCII characters unescaped, and escape only the quote, the backslash and the C0
 * control range. A renderer that differs anywhere puts the truncation cut in a different
 * place.
 */
public final class Json {

  private Json() {}

  /** Builds a field map whose iteration order is the order the fields are given in;
   *  the rendering is positional, so this order is part of the contract. */
  public static Map<String, String> fields(String... keyThenValue) {
    if (keyThenValue.length % 2 != 0) {
      throw new IllegalArgumentException("fields() takes key/value pairs");
    }
    var map = new LinkedHashMap<String, String>();
    for (int i = 0; i < keyThenValue.length; i += 2) {
      map.put(keyThenValue[i], keyThenValue[i + 1]);
    }
    return map;
  }

  public static String object(Map<String, String> fields) {
    var sb = new StringBuilder("{");
    boolean first = true;
    for (var e : fields.entrySet()) {
      if (!first) {
        sb.append(", ");
      }
      first = false;
      quote(sb, e.getKey());
      sb.append(": ");
      quote(sb, e.getValue());
    }
    return sb.append('}').toString();
  }

  private static void quote(StringBuilder sb, String s) {
    sb.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        case '\b' -> sb.append("\\b");
        case '\f' -> sb.append("\\f");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    sb.append('"');
  }
}
