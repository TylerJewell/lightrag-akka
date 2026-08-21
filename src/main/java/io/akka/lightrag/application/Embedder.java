package io.akka.lightrag.application;

/**
 * A deterministic stand-in for an embedding model.
 *
 * <p>SPEC-001 §1 puts the embedding model out of scope: a timing that includes a call to
 * one measures that service, and an answer comparison against a remote model is not
 * reproducible. This projects text onto a fixed number of dimensions by hashing its
 * tokens, so the same text always gives the same vector and similar texts share
 * dimensions. It is not a semantic embedding and is not offered as one — a caller with a
 * real model supplies vectors directly.
 */
public final class Embedder {

  public static final int DIMENSIONS = 128;

  private Embedder() {}

  public static float[] embed(String text) {
    float[] v = new float[DIMENSIONS];
    if (text == null || text.isBlank()) {
      return v;
    }
    for (String token : text.toLowerCase().split("[^\\p{L}\\p{N}]+")) {
      if (token.isEmpty()) {
        continue;
      }
      int h = token.hashCode();
      v[Math.floorMod(h, DIMENSIONS)] += 1.0f;
      // A second, differently-derived dimension per token, so two tokens colliding on
      // one dimension are still distinguishable.
      v[Math.floorMod(h * 31 + 7, DIMENSIONS)] += 0.5f;
    }
    return v;
  }
}
