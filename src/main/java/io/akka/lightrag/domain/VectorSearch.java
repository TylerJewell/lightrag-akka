package io.akka.lightrag.domain;

import io.akka.lightrag.domain.Model.Hit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Exact cosine ranking over stored vectors.
 *
 * <p>This is the one part of the retrieval path the source does not own: LightRAG hands a
 * query vector, a {@code top_k} and a cosine floor to the {@code nano-vectordb} package
 * and reshapes the rows that come back, so the ordering among equal scores is that
 * package's and not the source's. SPEC-001 §4.1 gives this port its own rule instead:
 * score descending, then id ascending. Two identical descriptions embedded by the same
 * model score exactly equal, so the tie is reachable rather than theoretical, and an id
 * tie-break is the only one of the candidates that survives a re-shard.
 */
public final class VectorSearch {

  public static final double DEFAULT_COSINE_THRESHOLD = 0.2;

  /** Ranked first by score descending, then by id ascending. */
  public static final Comparator<Hit> RANKING =
      Comparator.comparingDouble(Hit::score).reversed().thenComparing(Hit::id);

  private VectorSearch() {}

  public static double cosine(float[] a, float[] b) {
    double dot = 0;
    double na = 0;
    double nb = 0;
    int n = Math.min(a.length, b.length);
    for (int i = 0; i < n; i++) {
      dot += (double) a[i] * b[i];
      na += (double) a[i] * a[i];
      nb += (double) b[i] * b[i];
    }
    if (na == 0 || nb == 0) {
      return 0.0;
    }
    return dot / (Math.sqrt(na) * Math.sqrt(nb));
  }

  /**
   * Merges per-shard hit lists into one global ranking.
   *
   * <p>Every shard is scanned in full and every shard returns at least its own top
   * {@code topK}, so this merge yields exactly the ranking a single unsharded scan would:
   * a record can only be displaced from the global top k by k records that outrank it,
   * and any such record is either in its own shard's top k or displaced by others that
   * are.
   */
  public static List<Hit> merge(List<List<Hit>> perShard, int topK, double threshold) {
    var all = new ArrayList<Hit>();
    for (var shard : perShard) {
      all.addAll(shard);
    }
    return rank(all, topK, threshold);
  }

  public static List<Hit> rank(List<Hit> hits, int topK, double threshold) {
    var kept = new ArrayList<Hit>(hits.size());
    for (var h : hits) {
      if (h.score() > threshold) {
        kept.add(h);
      }
    }
    kept.sort(RANKING);
    return kept.size() <= topK ? kept : new ArrayList<>(kept.subList(0, topK));
  }
}
