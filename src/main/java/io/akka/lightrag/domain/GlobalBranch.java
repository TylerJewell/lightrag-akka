package io.akka.lightrag.domain;

import io.akka.lightrag.domain.Model.EdgeKey;
import io.akka.lightrag.domain.Model.Hit;
import io.akka.lightrag.domain.Model.RetrievedEntity;
import io.akka.lightrag.domain.Model.RetrievedRelation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The high-level branch: relations that match the broad keywords, and the entities on
 * either end of them.
 *
 * <p>Nothing here is re-sorted. The relations keep the index's order, and the entities
 * inherit it — an entity's position is decided by the best-matching relation that names
 * it, and no entity here carries a degree rank at all.
 *
 * <p>Endpoints are harvested in the direction the index stored, first endpoint then
 * second, rather than in sorted order: the index's direction is what decides which of two
 * entities is listed first.
 */
public final class GlobalBranch {

  private GlobalBranch() {}

  public record Result(List<RetrievedRelation> relations, List<RetrievedEntity> entities) {}

  public static Result run(List<Hit> relationHits, Backend backend) {
    var relations = new ArrayList<RetrievedRelation>(relationHits.size());
    for (var hit : relationHits) {
      var declared = EdgeKey.fromStorageId(hit.id());
      // The index stores a direction; the key is sorted. Endpoint order below follows
      // the stored direction, which is what decides which entity is listed first.
      String[] pair = declared.storageId().equals(hit.id())
          ? new String[] {declared.a(), declared.b()}
          : new String[] {declared.b(), declared.a()};
      backend
          .edge(declared)
          .ifPresent(
              edge ->
                  relations.add(
                      RetrievedRelation.global(
                          pair[0],
                          pair[1],
                          edge.weight(),
                          edge.description(),
                          edge.filePath(),
                          edge.sourceIds(),
                          hit.createdAt())));
    }

    var endpoints = new LinkedHashSet<String>();
    for (var relation : relations) {
      endpoints.add(relation.src());
      endpoints.add(relation.tgt());
    }

    var entities = new ArrayList<RetrievedEntity>(endpoints.size());
    for (String name : endpoints) {
      backend
          .node(name)
          .ifPresent(
              node ->
                  entities.add(
                      new RetrievedEntity(
                          node.entityName(),
                          node.entityType(),
                          node.description(),
                          node.filePath(),
                          node.sourceIds(),
                          null,
                          null)));
    }

    return new Result(List.copyOf(relations), List.copyOf(entities));
  }

}
