package io.akka.lightrag.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import io.akka.lightrag.application.AkkaBackend;
import io.akka.lightrag.domain.Model.Mode;
import io.akka.lightrag.domain.Model.QuerySpec;
import io.akka.lightrag.domain.RetrievalPipeline;
import java.util.List;

/** The read side: one query in, one ordered context out. */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@HttpEndpoint("/retrieve")
public class RetrievalEndpoint {

  private final ComponentClient componentClient;

  public RetrievalEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  /**
   * @param mode one of local, global, hybrid, mix
   * @param lowLevelKeywords the specific terms. A model produces these in the source;
   *     here the caller supplies them, so retrieval is reproducible.
   * @param highLevelKeywords the broad ones
   */
  public record Request(
      String query,
      String mode,
      List<String> lowLevelKeywords,
      List<String> highLevelKeywords,
      Integer topK,
      Integer chunkTopK,
      Integer maxEntityTokens,
      Integer maxRelationTokens,
      Integer chunkTokenBudget,
      Integer relatedChunkNumber) {}

  @Post
  public RetrievalResponse retrieve(Request request) {
    var mode = Mode.valueOf(request.mode() == null ? "MIX" : request.mode().toUpperCase());
    var defaults = QuerySpec.defaults(mode);
    var spec =
        new QuerySpec(
            mode,
            or(request.topK(), defaults.topK()),
            or(request.chunkTopK(), defaults.chunkTopK()),
            or(request.maxEntityTokens(), defaults.maxEntityTokens()),
            or(request.maxRelationTokens(), defaults.maxRelationTokens()),
            or(request.chunkTokenBudget(), defaults.chunkTokenBudget()),
            or(request.relatedChunkNumber(), defaults.relatedChunkNumber()));

    var query =
        new RetrievalPipeline.Query(
            request.query() == null ? "" : request.query(),
            request.lowLevelKeywords() == null ? List.of() : request.lowLevelKeywords(),
            request.highLevelKeywords() == null ? List.of() : request.highLevelKeywords());

    return RetrievalResponse.from(
        RetrievalPipeline.retrieve(query, spec, new AkkaBackend(componentClient)));
  }

  private static int or(Integer given, int fallback) {
    return given == null ? fallback : given;
  }
}
