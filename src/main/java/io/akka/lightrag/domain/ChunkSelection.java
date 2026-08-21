package io.akka.lightrag.domain;

import io.akka.lightrag.domain.Model.RetrievedEntity;
import io.akka.lightrag.domain.Model.RetrievedRelation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which text chunks each entity and each relation contributes.
 *
 * <p>Two rules that look like details and are not. A chunk several entities name belongs
 * to the <em>first</em> of them and to no other, but is still counted once per entity that
 * named it — and that count then reorders the owning entity's own list, so being widely
 * cited promotes a chunk within one entity rather than spreading it across several. And
 * the per-owner quota is a gradient rather than a share: the first owner is asked for
 * {@code relatedChunkNumber} chunks and the last for one, with the shortfall handed back
 * from the top.
 */
public final class ChunkSelection {

  private ChunkSelection() {}

  /**
   * @param chunkIds the selected chunk ids, in the order they were selected
   * @param occurrences how many owners named each chunk, which is what a caller reports
   *     as a chunk's frequency
   */
  public record Attribution(List<String> chunkIds, Map<String, Integer> occurrences) {}

  public static Attribution fromEntities(List<RetrievedEntity> entities, int relatedChunkNumber) {
    var owners = new ArrayList<List<String>>(entities.size());
    for (var e : entities) {
      owners.add(e.sourceIds());
    }
    return attribute(owners, Set.of(), relatedChunkNumber, false);
  }

  public static Attribution fromRelations(
      List<RetrievedRelation> relations, Set<String> alreadyTaken, int relatedChunkNumber) {
    var owners = new ArrayList<List<String>>(relations.size());
    for (var r : relations) {
      owners.add(r.sourceIds());
    }
    return attribute(owners, alreadyTaken, relatedChunkNumber, true);
  }

  /**
   * @param dropEmptied whether an owner left with nothing after deduplication still
   *     counts towards the quota split. The two passes answer this differently and the
   *     answer moves every quota: the entity pass keeps an emptied entity in the split,
   *     the relation pass removes it, so an entity whose every chunk went to an earlier
   *     entity still shortens the gradient while a relation in the same position does not.
   */
  private static Attribution attribute(
      List<List<String>> owners, Set<String> excluded, int relatedChunkNumber, boolean dropEmptied) {
    var occurrences = new LinkedHashMap<String, Integer>();
    var perOwner = new ArrayList<List<String>>(owners.size());

    for (var chunkIds : owners) {
      if (chunkIds.isEmpty()) {
        continue;
      }
      var kept = new ArrayList<String>();
      for (String chunkId : chunkIds) {
        if (excluded.contains(chunkId)) {
          continue;
        }
        int count = occurrences.merge(chunkId, 1, Integer::sum);
        if (count == 1) {
          kept.add(chunkId);
        }
      }
      if (!kept.isEmpty() || !dropEmptied) {
        perOwner.add(kept);
      }
    }

    // A chunk several owners named sits higher in the list of whichever owner kept it.
    for (var kept : perOwner) {
      kept.sort(Comparator.comparingInt((String id) -> occurrences.getOrDefault(id, 0)).reversed());
    }

    return new Attribution(
        weightedPolling(perOwner, relatedChunkNumber, 1), Map.copyOf(occurrences));
  }

  /**
   * A linear gradient of quotas from {@code max} for the first owner to {@code min} for
   * the last, followed by a pass that hands the unfilled quota back one chunk at a time,
   * always to the first owner with anything left.
   */
  public static List<String> weightedPolling(List<List<String>> owners, int max, int min) {
    if (owners.isEmpty() || max <= 0) {
      return List.of();
    }
    int n = owners.size();
    if (n == 1) {
      var only = owners.get(0);
      return List.copyOf(only.subList(0, Math.min(max, only.size())));
    }

    var selected = new ArrayList<String>();
    var used = new int[n];
    int unfilled = 0;
    for (int i = 0; i < n; i++) {
      double ratio = (double) i / (n - 1);
      int expected = (int) Math.round(max - ratio * (max - min));
      var chunkIds = owners.get(i);
      int taken = Math.min(expected, chunkIds.size());
      selected.addAll(chunkIds.subList(0, taken));
      used[i] = taken;
      unfilled += Math.max(0, expected - taken);
    }

    for (int pass = 0; pass < unfilled; pass++) {
      boolean handed = false;
      for (int i = 0; i < n; i++) {
        var chunkIds = owners.get(i);
        if (used[i] < chunkIds.size()) {
          selected.add(chunkIds.get(used[i]));
          used[i]++;
          handed = true;
          break;
        }
      }
      if (!handed) {
        break;
      }
    }
    return List.copyOf(selected);
  }
}
