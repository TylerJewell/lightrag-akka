package io.akka.lightrag.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.akka.lightrag.domain.Model.Chunk;
import io.akka.lightrag.domain.Model.FinalChunk;
import io.akka.lightrag.domain.Model.Reference;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** SPEC-001 R21 — reference ids, which belong to the survivor set rather than the chunk. */
public class ReferenceListTest {

  private static Chunk chunk(String id, String path) {
    return new Chunk(id, "body of " + id, path);
  }

  // v.md is cited twice but is not the first path seen, so citation count and first
  // appearance disagree -- which is the only arrangement that shows which one leads.
  private static final List<Chunk> TEN =
      List.of(chunk("c2", "2.md"), chunk("cv1", "v.md"), chunk("c8", "8.md"),
              chunk("cv2", "v.md"), chunk("c3", "3.md"), chunk("c9", "9.md"),
              chunk("c1", "1.md"), chunk("c5", "5.md"), chunk("c7", "7.md"),
              chunk("c4", "4.md"));

  @Test
  public void referenceIdsRankFilePathsByCountThenFirstSight() {
    var out = ReferenceList.assign(TEN.subList(0, 4));

    // v.md is cited twice and takes id 1; the singly-cited paths follow in the order
    // they were first seen.
    assertEquals(
        List.of(new Reference("1", "v.md"), new Reference("2", "2.md"), new Reference("3", "8.md")),
        out.references());
    assertEquals(
        Map.of("c2", "2", "cv1", "1", "c8", "3", "cv2", "1"),
        out.chunks().stream().collect(Collectors.toMap(FinalChunk::chunkId, FinalChunk::referenceId)));
  }

  @Test
  public void aReferenceIdMovesWhenTheSurvivorSetChanges() {
    var narrow = ids(ReferenceList.assign(TEN.subList(0, 4)));
    var wide = ids(ReferenceList.assign(TEN));

    assertEquals("3", narrow.get("c8"));
    assertEquals("3", wide.get("c8"));
    assertEquals("5", wide.get("c9"));
    assertEquals("9", wide.get("c4"));
  }

  @Test
  public void anUnknownSourceGetsNoReferenceIdAndNoEntry() {
    var out =
        ReferenceList.assign(
            List.of(chunk("a", "unknown_source"), chunk("b", ""), chunk("c", "real.md")));

    assertEquals(List.of(new Reference("1", "real.md")), out.references());
    assertEquals(Map.of("a", "", "b", "", "c", "1"), ids(out));
  }

  private static Map<String, String> ids(ReferenceList.Result out) {
    return out.chunks().stream()
        .collect(Collectors.toMap(FinalChunk::chunkId, FinalChunk::referenceId));
  }
}
