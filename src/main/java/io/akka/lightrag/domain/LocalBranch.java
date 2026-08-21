package io.akka.lightrag.domain;

import io.akka.lightrag.domain.Model.EdgeKey;
import io.akka.lightrag.domain.Model.Hit;
import io.akka.lightrag.domain.Model.RetrievedEntity;
import io.akka.lightrag.domain.Model.RetrievedRelation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The low-level branch: entities that match the specific keywords, and the relations they
 * sit on.
 *
 * <p>The two halves are ordered by different things. Entities keep the index's order, so
 * they are ranked by similarity. Relations are re-sorted by graph shape — degree, then
 * weight — so a relation's position here has nothing to do with how well anything
 * matched. Both facts survive into the fused list and neither is recoverable from it.
 */
public final class LocalBranch {

  /** Descending on rank, then on weight. Applied as a stable sort, so records that tie on
   *  both keep the order they were collected in. */
  static final Comparator<RetrievedRelation> BY_RANK_THEN_WEIGHT =
      Comparator.comparingInt((RetrievedRelation r) -> r.rank() == null ? 0 : r.rank())
          .thenComparingDouble(RetrievedRelation::weight)
          .reversed();

  private LocalBranch() {}

  public record Result(List<RetrievedEntity> entities, List<RetrievedRelation> relations) {}

  public static Result run(List<Hit> entityHits, Backend backend) {
    var entities = new ArrayList<RetrievedEntity>(entityHits.size());
    for (var hit : entityHits) {
      backend
          .node(hit.id())
          .ifPresent(
              node ->
                  entities.add(
                      new RetrievedEntity(
                          node.entityName(),
                          node.entityType(),
                          node.description(),
                          node.filePath(),
                          node.sourceIds(),
                          backend.degree(node.entityName()),
                          hit.createdAt())));
    }

    // Collected entity by entity in order, each entity's edges in store order, keyed on
    // the sorted pair so an edge two entities share is collected once.
    var collected = new LinkedHashSet<EdgeKey>();
    for (var entity : entities) {
      for (String neighbour : backend.neighbours(entity.entityName())) {
        collected.add(EdgeKey.of(entity.entityName(), neighbour));
      }
    }

    var relations = new ArrayList<RetrievedRelation>(collected.size());
    for (var key : collected) {
      backend
          .edge(key)
          .ifPresent(
              edge ->
                  relations.add(
                      RetrievedRelation.local(
                          key,
                          edge.weight(),
                          edge.description(),
                          edge.filePath(),
                          edge.sourceIds(),
                          backend.degree(key.a()) + backend.degree(key.b()))));
    }
    relations.sort(BY_RANK_THEN_WEIGHT);

    return new Result(List.copyOf(entities), List.copyOf(relations));
  }
}
