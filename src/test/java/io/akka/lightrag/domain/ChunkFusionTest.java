package io.akka.lightrag.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.akka.lightrag.domain.Model.Chunk;
import java.util.List;
import org.junit.jupiter.api.Test;

/** SPEC-001 R19, R20 — the chunk round robin and the top-k cut. */
public class ChunkFusionTest {

  private static Chunk chunk(String id, String path) {
    return new Chunk(id, "body of " + id, path);
  }

  private static final List<Chunk> VECTOR = List.of(chunk("cv1", "v.md"), chunk("cv2", "v.md"));
  private static final List<Chunk> ENTITY =
      List.of(chunk("c2", "2.md"), chunk("c3", "3.md"), chunk("c1", "1.md"),
              chunk("c5", "5.md"), chunk("c7", "7.md"), chunk("c4", "4.md"));
  private static final List<Chunk> RELATION = List.of(chunk("c8", "8.md"), chunk("c9", "9.md"));

  @Test
  public void chunksFuseVectorThenEntityThenRelation() {
    var fused = ChunkFusion.fuse(VECTOR, ENTITY, RELATION);

    assertEquals(
        List.of("cv1", "c2", "c8", "cv2", "c3", "c9", "c1", "c5", "c7", "c4"),
        Fixtures.chunkIds(fused));
  }

  @Test
  public void aChunkInTwoSourcesIsPlacedByWhicheverOfferedItFirst() {
    var fused = ChunkFusion.fuse(List.of(chunk("shared", "v.md")), List.of(chunk("shared", "2.md")), List.of());

    assertEquals(List.of("shared"), Fixtures.chunkIds(fused));
    assertEquals("v.md", fused.get(0).filePath());
  }

  @Test
  public void withNoGraphHitsTheMergeIsTheVectorChunksUnchanged() {
    var fused = ChunkFusion.fuse(VECTOR, List.of(), List.of());

    assertEquals(List.of("cv1", "cv2"), Fixtures.chunkIds(fused));
  }

  @Test
  public void chunkTopKIsAPrefixCutAndNumbersSurvivors() {
    var fused = ChunkFusion.fuse(VECTOR, ENTITY, RELATION);
    var kept = ChunkBudget.cut(fused, 4, 100_000);
    var assigned = ReferenceList.assign(kept);

    assertEquals(List.of("cv1", "c2", "c8", "cv2"), Fixtures.chunkIds(kept));
    assertEquals(
        List.of("DC1", "DC2", "DC3", "DC4"),
        assigned.chunks().stream().map(Model.FinalChunk::id).toList());
  }
}
