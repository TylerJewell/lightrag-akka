package io.akka.lightrag.domain;

import io.akka.lightrag.domain.Model.Chunk;
import io.akka.lightrag.domain.Model.FinalChunk;
import io.akka.lightrag.domain.Model.Reference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Numbering the surviving chunks and citing the files they came from.
 *
 * <p>A reference id is a property of the survivor set, not of the chunk: the file paths
 * are ranked by how many surviving chunks cite each, so widening or narrowing the cut
 * renumbers everything. Nothing downstream may cache a reference id across two different
 * cuts.
 */
public final class ReferenceList {

  public static final String UNKNOWN_SOURCE = "unknown_source";

  private ReferenceList() {}

  public record Result(List<Reference> references, List<FinalChunk> chunks) {}

  public static Result assign(List<Chunk> survivors) {
    var counts = new LinkedHashMap<String, Integer>();
    var firstSeen = new LinkedHashMap<String, Integer>();
    for (int i = 0; i < survivors.size(); i++) {
      String path = survivors.get(i).filePath();
      if (cited(path)) {
        counts.merge(path, 1, Integer::sum);
        firstSeen.putIfAbsent(path, i);
      }
    }

    var paths = new ArrayList<>(counts.keySet());
    paths.sort(
        Comparator.comparingInt((String p) -> counts.get(p)).reversed()
            .thenComparingInt(firstSeen::get));

    var referenceIds = new LinkedHashMap<String, String>();
    var references = new ArrayList<Reference>(paths.size());
    for (int i = 0; i < paths.size(); i++) {
      String id = String.valueOf(i + 1);
      referenceIds.put(paths.get(i), id);
      references.add(new Reference(id, paths.get(i)));
    }

    var chunks = new ArrayList<FinalChunk>(survivors.size());
    for (int i = 0; i < survivors.size(); i++) {
      var c = survivors.get(i);
      chunks.add(
          new FinalChunk(
              "DC" + (i + 1),
              c.chunkId(),
              c.content(),
              c.filePath(),
              referenceIds.getOrDefault(c.filePath(), "")));
    }
    return new Result(List.copyOf(references), List.copyOf(chunks));
  }

  /** The exact text a reader is sent, and therefore the text a token budget is measured
   *  against: one JSON object per line of {@code {reference_id, content}}. */
  public static String render(List<FinalChunk> chunks) {
    var lines = new ArrayList<String>(chunks.size());
    for (var c : chunks) {
      lines.add(Json.object(Json.fields("reference_id", c.referenceId(), "content", c.content())));
    }
    return String.join("\n", lines);
  }

  private static boolean cited(String path) {
    return path != null && !path.isEmpty() && !path.equals(UNKNOWN_SOURCE);
  }

  static Map<String, String> idsByPath(List<Reference> references) {
    var map = new LinkedHashMap<String, String>();
    for (var r : references) {
      map.put(r.filePath(), r.referenceId());
    }
    return map;
  }
}
