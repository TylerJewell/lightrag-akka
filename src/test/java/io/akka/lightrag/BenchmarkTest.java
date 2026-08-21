package io.akka.lightrag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.akka.lightrag.application.InMemoryBackend;
import io.akka.lightrag.domain.Backend;
import io.akka.lightrag.domain.Model.Chunk;
import io.akka.lightrag.domain.Model.Edge;
import io.akka.lightrag.domain.Model.EdgeKey;
import io.akka.lightrag.domain.Model.Hit;
import io.akka.lightrag.domain.Model.Mode;
import io.akka.lightrag.domain.Model.Node;
import io.akka.lightrag.domain.Model.QuerySpec;
import io.akka.lightrag.domain.Model.RetrievalResult;
import io.akka.lightrag.domain.RetrievalPipeline;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The same workloads the original was run against, run here.
 *
 * <p>Two jobs in one pass, in this order. First: does this port give the same answers?
 * Every workload's answer is compared against `answers-python.json`, which
 * `lightrag-port/bench/run_source.py` produced by driving the upstream functions
 * themselves. Second, and only because the first passed: how long does each take. Both
 * files are checked into `src/test/resources/bench/` so this reproduces without the
 * original installed.
 *
 * <p>Timings are written to `target/bench-java.json`. They cover the retrieval pipeline
 * over an in-memory index and no runtime — the same boundary the source side timed, so
 * neither number includes a network, a model or a database.
 */
public class BenchmarkTest {

  private static final int WARMUP = 200;
  private static final int REPEATS = 300;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static JsonNode resource(String name) throws IOException {
    try (InputStream in = BenchmarkTest.class.getResourceAsStream("/bench/" + name)) {
      if (in == null) {
        throw new IllegalStateException("missing test resource /bench/" + name);
      }
      return MAPPER.readTree(in);
    }
  }

  private static List<String> strings(JsonNode array) {
    var out = new ArrayList<String>();
    array.forEach(n -> out.add(n.asText()));
    return out;
  }

  /** The relation index stores a direction, so its record id is not the sorted pair. */
  private static String directedRelationId(String src, String tgt) {
    return URLEncoder.encode(src, StandardCharsets.UTF_8)
        + ","
        + URLEncoder.encode(tgt, StandardCharsets.UTF_8);
  }

  private static InMemoryBackend backend(JsonNode workload, List<JsonNode> edges) {
    var b = new InMemoryBackend();
    for (JsonNode n : workload.get("nodes")) {
      b.addNode(
          new Node(
              n.get("entityName").asText(),
              n.get("entityType").asText(),
              n.get("description").asText(),
              n.get("filePath").asText(),
              strings(n.get("sourceIds"))));
    }
    for (JsonNode e : edges) {
      b.addEdge(
          new Edge(
              EdgeKey.of(e.get("src").asText(), e.get("tgt").asText()),
              e.get("weight").asDouble(),
              e.get("description").asText(),
              e.get("filePath").asText(),
              strings(e.get("sourceIds"))));
    }
    for (JsonNode c : workload.get("chunks")) {
      b.addChunk(
          new Chunk(
              c.get("chunkId").asText(),
              c.get("content").asText(),
              c.get("filePath").asText()));
    }

    var entityHits = new ArrayList<Hit>();
    int i = 0;
    for (JsonNode h : workload.get("entityHits")) {
      entityHits.add(new Hit(h.asText(), 1.0 - i * 0.01, (long) (100 + i)));
      i++;
    }
    var relationHits = new ArrayList<Hit>();
    i = 0;
    for (JsonNode h : workload.get("relationHits")) {
      String[] pair = h.asText().split("\\|", 2);
      relationHits.add(
          new Hit(directedRelationId(pair[0], pair[1]), 1.0 - i * 0.01, (long) (200 + i)));
      i++;
    }
    var chunkHits = new ArrayList<Hit>();
    i = 0;
    for (JsonNode h : workload.get("chunkHits")) {
      chunkHits.add(new Hit(h.asText(), 1.0 - i * 0.01, (long) (300 + i)));
      i++;
    }
    return b.scriptEntityHits(entityHits)
        .scriptRelationHits(relationHits)
        .scriptChunkHits(chunkHits);
  }

  private static List<JsonNode> edgesOf(JsonNode workload) {
    var out = new ArrayList<JsonNode>();
    JsonNode edges = workload.has("edges") ? workload.get("edges") : workload.get("rows");
    if (edges != null) {
      edges.forEach(out::add);
    }
    return out;
  }

  private static QuerySpec specOf(JsonNode workload) {
    JsonNode s = workload.get("spec");
    return new QuerySpec(
        Mode.valueOf(workload.get("mode").asText().toUpperCase()),
        s.get("topK").asInt(),
        s.get("chunkTopK").asInt(),
        s.get("maxEntityTokens").asInt(),
        s.get("maxRelationTokens").asInt(),
        s.get("chunkTokenBudget").asInt(),
        s.get("relatedChunkNumber").asInt());
  }

  private static RetrievalPipeline.Query queryOf(JsonNode workload) {
    return new RetrievalPipeline.Query(
        workload.get("query").asText(),
        strings(workload.get("llKeywords")),
        strings(workload.get("hlKeywords")));
  }

  /** The comparable shape: what came back and in what order, with nothing else. */
  private static ObjectNode answer(RetrievalResult result) {
    var node = MAPPER.createObjectNode();
    ArrayNode entities = node.putArray("entities");
    result.entities().forEach(e -> entities.addObject()
        .put("entity", e.entity())
        .put("type", e.type())
        .put("description", e.description()));
    ArrayNode relations = node.putArray("relations");
    result.relations().forEach(r -> relations.addObject()
        .put("entity1", r.entity1())
        .put("entity2", r.entity2())
        .put("description", r.description()));
    ArrayNode chunks = node.putArray("chunks");
    result.chunks().forEach(c -> chunks.addObject()
        .put("id", c.id())
        .put("chunkId", c.chunkId())
        .put("referenceId", c.referenceId()));
    ArrayNode references = node.putArray("references");
    result.references().forEach(r -> references.addObject()
        .put("referenceId", r.referenceId())
        .put("filePath", r.filePath()));
    return node;
  }

  private static List<List<JsonNode>> permutations(List<JsonNode> rows) {
    var out = new ArrayList<List<JsonNode>>();
    permute(new ArrayList<>(rows), 0, out);
    return out;
  }

  private static void permute(List<JsonNode> rows, int at, List<List<JsonNode>> out) {
    if (at == rows.size()) {
      out.add(new ArrayList<>(rows));
      return;
    }
    for (int i = at; i < rows.size(); i++) {
      // Rotate rather than swap, so the permutations come out in the same lexicographic
      // order Python's itertools.permutations produces and the two answer lists line up
      // position by position.
      var next = new ArrayList<>(rows);
      var moved = next.remove(i);
      next.add(at, moved);
      permute(next, at + 1, out);
    }
  }

  @Test
  public void givesTheSameAnswersAsTheOriginalAndRecordsHowLongItTakes() throws IOException {
    JsonNode workloads = resource("workloads.json");
    JsonNode expected = resource("answers-python.json");

    var timings = MAPPER.createObjectNode();
    int compared = 0;

    for (JsonNode workload : workloads) {
      String name = workload.get("name").asText();
      JsonNode want = expected.get(name);
      assertTrue(want != null, "no recorded original answer for workload " + name);

      if (workload.has("sequence")) {
        var rows = edgesOf(workload);
        var orders = permutations(rows);
        assertEquals(
            want.get("deliveryOrders").asInt(),
            orders.size(),
            name + ": both sides must try the same number of delivery orders");
        var distinct = new java.util.HashSet<String>();
        for (int i = 0; i < orders.size(); i++) {
          Backend b = backend(workload, orders.get(i));
          var got = answer(RetrievalPipeline.retrieve(queryOf(workload), specOf(workload), b));
          // Compared as trees: object field order is not part of the answer, list order
          // is the whole of it, and JsonNode equality draws exactly that line.
          assertEquals(
              want.get("answers").get(i), (JsonNode) got, name + ": delivery order " + i);
          distinct.add(got.toString());
          compared++;
        }
        // A workload that ties on the sort key and still gives one answer proves nothing:
        // it looks the same whether delivery order is ignored or whether the varied
        // records never reached the decision that sorts. The one declared to be
        // order-dependent has to actually move, or every other order comparison here is
        // being read off an experiment nobody showed could fail.
        if (workload.path("expectsDistinctAnswers").asBoolean()) {
          assertTrue(
              distinct.size() > 1,
              name + ": declares that the answer moves with delivery order, and gave the "
                  + "same answer to all " + orders.size() + " of them");
        }
        continue;
      }

      Backend b = backend(workload, edgesOf(workload));
      var got = answer(RetrievalPipeline.retrieve(queryOf(workload), specOf(workload), b));
      assertEquals(want, (JsonNode) got, name);
      compared++;

      for (int i = 0; i < WARMUP; i++) {
        RetrievalPipeline.retrieve(queryOf(workload), specOf(workload), b);
      }
      var samples = new long[REPEATS];
      for (int i = 0; i < REPEATS; i++) {
        Backend fresh = backend(workload, edgesOf(workload));
        long start = System.nanoTime();
        RetrievalPipeline.retrieve(queryOf(workload), specOf(workload), fresh);
        samples[i] = System.nanoTime() - start;
      }
      java.util.Arrays.sort(samples);
      var entry = timings.putObject(name);
      entry.put("repeats", REPEATS);
      entry.put("medianNanos", samples[samples.length / 2]);
      entry.put("minNanos", samples[0]);
    }

    assertTrue(compared >= 15, "expected every workload compared, was " + compared);

    var out = Path.of("target", "bench-java.json");
    Files.createDirectories(out.getParent());
    Files.writeString(
        out,
        MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(sorted(timings)) + "\n",
        StandardCharsets.UTF_8);
    System.out.println("wrote " + out.toAbsolutePath() + " (" + compared + " answers compared)");
  }

  private static ObjectNode sorted(ObjectNode node) {
    var keys = new ArrayList<String>();
    node.fieldNames().forEachRemaining(keys::add);
    keys.sort(String::compareTo);
    Map<String, JsonNode> ordered = new LinkedHashMap<>();
    keys.forEach(k -> ordered.put(k, node.get(k)));
    var out = MAPPER.createObjectNode();
    ordered.forEach(out::set);
    return out;
  }
}
