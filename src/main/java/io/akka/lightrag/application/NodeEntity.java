package io.akka.lightrag.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import io.akka.lightrag.domain.Model.Node;
import java.util.ArrayList;
import java.util.List;

/**
 * One entity of the knowledge graph, holding its own adjacency.
 *
 * <p>The adjacency lives here rather than being derived from the edges because the local
 * branch needs a node's degree and its incident edges for every hit, and a degree read
 * has to be one call rather than a scan. It is a list rather than a set so that the order
 * neighbours were linked in survives — that order is the tie-break when two incident
 * edges rank equally.
 */
@Component(id = "node")
public class NodeEntity extends KeyValueEntity<NodeEntity.State> {

  /** @param node null until the node itself is written; a node can be linked before it
   *      is described, because an edge may arrive first. */
  public record State(Node node, List<String> neighbours) {}

  @Override
  public State emptyState() {
    return new State(null, List.of());
  }

  public Effect<String> put(Node node) {
    return effects().updateState(new State(node, currentState().neighbours())).thenReply("ok");
  }

  public Effect<Integer> link(String neighbour) {
    if (currentState().neighbours().contains(neighbour)) {
      return effects().reply(currentState().neighbours().size());
    }
    var next = new ArrayList<>(currentState().neighbours());
    next.add(neighbour);
    return effects().updateState(new State(currentState().node(), next)).thenReply(next.size());
  }

  public ReadOnlyEffect<State> get() {
    return effects().reply(currentState());
  }
}
