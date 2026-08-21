package io.akka.lightrag.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.CommandException;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpException;
import akka.javasdk.http.HttpResponses;
import akka.http.javadsl.model.HttpResponse;
import io.akka.lightrag.application.AkkaBackend;
import io.akka.lightrag.application.ChunkEntity;
import io.akka.lightrag.application.EdgeEntity;
import io.akka.lightrag.application.Embedder;
import io.akka.lightrag.application.Ids;
import io.akka.lightrag.application.NodeEntity;
import io.akka.lightrag.application.VectorShardEntity;
import io.akka.lightrag.domain.Model.Chunk;
import io.akka.lightrag.domain.Model.Edge;
import io.akka.lightrag.domain.Model.EdgeKey;
import io.akka.lightrag.domain.Model.Node;
import java.util.List;

/**
 * The write side: enough of one to fill an index and ask it something.
 *
 * <p>Extracting entities and relations out of documents is the other half of the source
 * and out of this port's scope (SPEC-001 §1), so what a caller posts here is already
 * extracted.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@HttpEndpoint("/index")
public class IndexEndpoint {

  private final ComponentClient componentClient;

  public IndexEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public record NodeRequest(
      String entityName,
      String entityType,
      String description,
      String filePath,
      List<String> sourceIds) {}

  public record EdgeRequest(
      String src,
      String tgt,
      double weight,
      String description,
      String filePath,
      List<String> sourceIds) {}

  public record ChunkRequest(String chunkId, String content, String filePath) {}

  @Post("/node")
  public HttpResponse node(NodeRequest request) {
    var node =
        new Node(
            request.entityName(),
            request.entityType(),
            request.description(),
            request.filePath(),
            request.sourceIds() == null ? List.of() : request.sourceIds());
    componentClient
        .forKeyValueEntity(Ids.node(node.entityName()))
        .method(NodeEntity::put)
        .invoke(node);
    index(
        AkkaBackend.ENTITY_INDEX,
        node.entityName(),
        node.entityName() + " " + node.description());
    return HttpResponses.ok();
  }

  @Post("/edge")
  public HttpResponse edge(EdgeRequest request) {
    var key = EdgeKey.of(request.src(), request.tgt());
    var edge =
        new Edge(
            key,
            request.weight(),
            request.description(),
            request.filePath(),
            request.sourceIds() == null ? List.of() : request.sourceIds());
    componentClient.forKeyValueEntity(Ids.edge(key)).method(EdgeEntity::put).invoke(edge);
    // Both directions, because a node's degree counts every incident edge and the graph
    // is undirected.
    componentClient.forKeyValueEntity(Ids.node(key.a())).method(NodeEntity::link).invoke(key.b());
    componentClient.forKeyValueEntity(Ids.node(key.b())).method(NodeEntity::link).invoke(key.a());
    index(AkkaBackend.RELATION_INDEX, key.storageId(), edge.description());
    return HttpResponses.ok();
  }

  @Post("/chunk")
  public HttpResponse chunk(ChunkRequest request) {
    var chunk = new Chunk(request.chunkId(), request.content(), request.filePath());
    componentClient.forKeyValueEntity(Ids.chunk(chunk.chunkId())).method(ChunkEntity::put).invoke(chunk);
    index(AkkaBackend.CHUNK_INDEX, chunk.chunkId(), chunk.content());
    return HttpResponses.ok();
  }

  /**
   * A shard that is full refuses with a message naming its cap. Left to the default that
   * becomes an opaque 400, so it is caught here and returned as a bad request carrying
   * the shard's own words — which is the only place the caller learns why.
   */
  private void index(String indexName, String recordId, String text) {
    try {
      componentClient
          .forKeyValueEntity(AkkaBackend.shardId(indexName, recordId))
          .method(VectorShardEntity::upsert)
          .invoke(
              new VectorShardEntity.Upsert(
                  List.of(new VectorShardEntity.Vector(recordId, Embedder.embed(text), null))));
    } catch (CommandException e) {
      throw HttpException.badRequest(e.getMessage());
    }
  }
}
