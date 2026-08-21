package io.akka.lightrag.application;

import io.akka.lightrag.domain.Model.EdgeKey;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Entity ids derived from names the port does not control.
 *
 * <p>An extracted entity name is arbitrary text, and the runtime reserves the vertical
 * bar in an entity id — a bar reaches the caller as a ten-second timeout naming no
 * character, so nothing downstream can tell it apart from a slow service. Percent-encoding
 * every name removes the whole class rather than the one character: the encoded form uses
 * only letters, digits and a handful of punctuation, none of it reserved, and a comma
 * between two encoded halves cannot be confused with a comma inside a name because that
 * one is encoded to {@code %2C}.
 */
public final class Ids {

  private Ids() {}

  public static String node(String entityName) {
    return encode(entityName);
  }

  public static String edge(EdgeKey key) {
    return key.storageId();
  }

  public static String chunk(String chunkId) {
    return encode(chunkId);
  }

  private static String encode(String raw) {
    return URLEncoder.encode(raw, StandardCharsets.UTF_8);
  }
}
