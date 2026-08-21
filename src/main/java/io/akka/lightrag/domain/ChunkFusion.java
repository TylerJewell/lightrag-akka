package io.akka.lightrag.domain;

import io.akka.lightrag.domain.Model.Chunk;
import java.util.List;

/**
 * The second round robin: the plain vector search, the entity-derived chunks and the
 * relation-derived chunks, interleaved one at a time in that order.
 *
 * <p>The order of the three lists is the priority order, so a chunk two of them hold
 * takes the earlier list's position and the earlier list's record.
 */
public final class ChunkFusion {

  private ChunkFusion() {}

  public static List<Chunk> fuse(List<Chunk> vector, List<Chunk> entity, List<Chunk> relation) {
    return RoundRobinFusion.fuse(List.of(vector, entity, relation), Chunk::chunkId);
  }
}
