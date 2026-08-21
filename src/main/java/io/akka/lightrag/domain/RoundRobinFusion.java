package io.akka.lightrag.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

/**
 * The fusion the source performs: interleave the result lists, drop repeats, keep the
 * first placement.
 *
 * <p>There is no score here, and that is the point. The lists arrive already ordered by
 * their own branch's rule — vector similarity for one, graph degree for another — and the
 * merge treats position as the whole of the ranking. A reciprocal-rank fusion, which is
 * what "rank fusion" usually names, would combine those two orderings into a third and
 * give a different answer on every query where the branches disagree.
 *
 * <p>The list order given here is the priority order: the first list is offered first at
 * every index, so a record two lists both hold keeps the first list's copy — which is a
 * different record, not just a different position (see {@code Model.RetrievedRelation}).
 */
public final class RoundRobinFusion {

  private RoundRobinFusion() {}

  public static <T, K> List<T> fuse(List<List<T>> lists, Function<T, K> key) {
    int longest = 0;
    for (var list : lists) {
      longest = Math.max(longest, list.size());
    }
    var out = new ArrayList<T>();
    var seen = new HashSet<K>();
    for (int i = 0; i < longest; i++) {
      for (var list : lists) {
        if (i < list.size()) {
          T item = list.get(i);
          if (seen.add(key.apply(item))) {
            out.add(item);
          }
        }
      }
    }
    return out;
  }
}
