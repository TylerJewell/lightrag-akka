package io.akka.lightrag.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.testkit.TestKitSupport;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * SPEC-001 §4.6 — the capability is reachable from outside a test.
 *
 * <p>Everything else about this port can be driven from a unit test, which is exactly why
 * this one exists: "no graphical surface" answers whether there is a screen, not whether
 * anything outside a test can reach the thing at all.
 */
public class RetrievalEndpointIntegrationTest extends TestKitSupport {

  private void seed() {
    node("APOLLO", "a rocket programme", List.of("c1", "c2"));
    node("SATURN", "a rocket family", List.of("c2", "c3"));
    node("HOUSTON", "a control centre", List.of("c3"));

    chunk("c1", "The programme was announced in nineteen sixty one.", "history.md");
    chunk("c2", "The rocket family carried the programme's crews.", "history.md");
    chunk("c3", "Flight control sat in the control centre.", "ops.md");

    edge("APOLLO", "SATURN", 2.0, "the programme flew on the rocket family", List.of("c2"));
    edge("SATURN", "HOUSTON", 1.0, "the rocket family was flown from the centre", List.of("c3"));
  }

  private void node(String name, String description, List<String> sourceIds) {
    var response =
        httpClient
            .POST("/index/node")
            .withRequestBody(
                new IndexEndpoint.NodeRequest(name, "THING", description, "history.md", sourceIds))
            .invoke();
    assertEquals(StatusCodes.OK, response.status());
  }

  private void edge(
      String src, String tgt, double weight, String description, List<String> sourceIds) {
    var response =
        httpClient
            .POST("/index/edge")
            .withRequestBody(
                new IndexEndpoint.EdgeRequest(src, tgt, weight, description, "history.md", sourceIds))
            .invoke();
    assertEquals(StatusCodes.OK, response.status());
  }

  private void chunk(String id, String content, String filePath) {
    var response =
        httpClient
            .POST("/index/chunk")
            .withRequestBody(new IndexEndpoint.ChunkRequest(id, content, filePath))
            .invoke();
    assertEquals(StatusCodes.OK, response.status());
  }

  private RetrievalResponse retrieve(RetrievalEndpoint.Request request) {
    var response =
        httpClient
            .POST("/retrieve")
            .withRequestBody(request)
            .responseBodyAs(RetrievalResponse.class)
            .invoke();
    assertEquals(StatusCodes.OK, response.status());
    return response.body();
  }

  @Test
  public void retrievesOverHttpEndToEnd() {
    seed();

    var result =
        retrieve(
            new RetrievalEndpoint.Request(
                "what carried the crews",
                "hybrid",
                List.of("rocket family"),
                List.of("programme"),
                40, 20, 6000, 8000, 12000, 5));

    assertFalse(result.entities().isEmpty(), "the low-level branch should find an entity");
    assertFalse(result.chunks().isEmpty(), "entities should pull their chunks in");
    assertTrue(
        result.chunks().stream().allMatch(c -> c.id().startsWith("DC")),
        "every surviving chunk is numbered");
    assertTrue(
        result.references().stream().anyMatch(r -> r.filePath().equals("history.md")),
        "the files the surviving chunks came from are cited");
  }

  @Test
  public void anEntityNameHoldingAReservedCharacterStillRoundTrips() {
    // An extracted entity name is arbitrary text. A vertical bar in an entity id is
    // refused by the runtime as a ten-second timeout naming no character, so a name
    // carrying one has to be encoded before it becomes an id.
    node("A|B, LTD", "a firm with punctuation in its name", List.of("cx"));
    chunk("cx", "The firm signed the contract.", "deals.md");

    var result =
        retrieve(
            new RetrievalEndpoint.Request(
                "which firm",
                "local",
                List.of("firm punctuation"),
                List.of(),
                40, 20, 6000, 8000, 12000, 5));

    assertTrue(
        result.entities().stream().anyMatch(e -> e.entity().equals("A|B, LTD")),
        "the entity should come back under its original name, was: " + result.entities());
  }

  @Test
  public void aQueryWithNoKeywordsAtEitherLevelReturnsAnEmptyContext() {
    seed();

    var result =
        retrieve(
            new RetrievalEndpoint.Request(
                "anything", "hybrid", List.of(), List.of(), 40, 20, 6000, 8000, 12000, 5));

    assertEquals(List.of(), result.entities());
    assertEquals(List.of(), result.relations());
    assertEquals(List.of(), result.chunks());
  }

  @Test
  public void localModeWithNoLowLevelKeywordsStillAnswersFromTheRelationIndex() {
    seed();

    var result =
        retrieve(
            new RetrievalEndpoint.Request(
                "what flew",
                "local",
                List.of(),
                List.of("rocket family flown"),
                40, 20, 6000, 8000, 12000, 5));

    // The behaviour SPEC-001 §4.3 decided to keep, reachable over the real surface.
    assertFalse(result.relations().isEmpty());
  }
}
