package io.akka.lightrag.api;

import io.akka.lightrag.domain.Model.RetrievalResult;
import java.util.List;

/**
 * What a caller receives, kept separate from the domain records the pipeline works in.
 *
 * <p>The two happen to have the same shape today. Keeping them apart is what lets the
 * pipeline's records change — a new field on a retrieved entity, a different internal
 * name — without moving the published contract underneath whoever is calling it.
 */
public record RetrievalResponse(
    List<Entity> entities,
    List<Relation> relations,
    List<Chunk> chunks,
    List<Reference> references) {

  public record Entity(String entity, String type, String description) {}

  public record Relation(String entity1, String entity2, String description) {}

  public record Chunk(String id, String chunkId, String content, String referenceId) {}

  public record Reference(String referenceId, String filePath) {}

  public static RetrievalResponse from(RetrievalResult result) {
    return new RetrievalResponse(
        result.entities().stream()
            .map(e -> new Entity(e.entity(), e.type(), e.description()))
            .toList(),
        result.relations().stream()
            .map(r -> new Relation(r.entity1(), r.entity2(), r.description()))
            .toList(),
        result.chunks().stream()
            .map(c -> new Chunk(c.id(), c.chunkId(), c.content(), c.referenceId()))
            .toList(),
        result.references().stream()
            .map(r -> new Reference(r.referenceId(), r.filePath()))
            .toList());
  }
}
