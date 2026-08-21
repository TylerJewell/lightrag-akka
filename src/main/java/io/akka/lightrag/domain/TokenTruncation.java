package io.akka.lightrag.domain;

import io.akka.lightrag.domain.Model.EntityContext;
import io.akka.lightrag.domain.Model.RelationContext;
import io.akka.lightrag.domain.Model.RetrievedEntity;
import io.akka.lightrag.domain.Model.RetrievedRelation;
import java.util.ArrayList;
import java.util.List;

/**
 * Cutting the entity and relation lists to a token budget.
 *
 * <p>Two things about the projection are load-bearing. It is the <em>stripped</em> record
 * that is counted and the stripped record that survives, so a file path and a timestamp
 * held all the way here never reach a reader. And the count is taken over the whole
 * rendered block, separators included, so the budget is what will actually be sent rather
 * than the sum of the parts.
 */
public final class TokenTruncation {

  static final String SEPARATOR = "\n";

  private TokenTruncation() {}

  public record Entities(List<EntityContext> context, List<RetrievedEntity> survivors) {}

  public record Relations(List<RelationContext> context, List<RetrievedRelation> survivors) {}

  public static Entities entities(List<RetrievedEntity> fused, int maxTokens) {
    var context = new ArrayList<EntityContext>(fused.size());
    var rendered = new ArrayList<String>(fused.size());
    for (var e : fused) {
      var projection = new EntityContext(e.entityName(), e.entityType(), e.description());
      context.add(projection);
      rendered.add(render(projection));
    }
    int k = Tokenizer.prefixThatFits(rendered, SEPARATOR, maxTokens);
    return new Entities(List.copyOf(context.subList(0, k)), List.copyOf(fused.subList(0, k)));
  }

  public static Relations relations(List<RetrievedRelation> fused, int maxTokens) {
    var context = new ArrayList<RelationContext>(fused.size());
    var rendered = new ArrayList<String>(fused.size());
    for (var r : fused) {
      var projection = new RelationContext(r.src(), r.tgt(), r.description());
      context.add(projection);
      rendered.add(render(projection));
    }
    int k = Tokenizer.prefixThatFits(rendered, SEPARATOR, maxTokens);
    return new Relations(List.copyOf(context.subList(0, k)), List.copyOf(fused.subList(0, k)));
  }

  public static String render(EntityContext e) {
    return Json.object(
        Json.fields("entity", e.entity(), "type", e.type(), "description", e.description()));
  }

  public static String render(RelationContext r) {
    return Json.object(
        Json.fields(
            "entity1", r.entity1(), "entity2", r.entity2(), "description", r.description()));
  }

  public static String renderBlock(List<String> rendered) {
    return String.join(SEPARATOR, rendered);
  }
}
