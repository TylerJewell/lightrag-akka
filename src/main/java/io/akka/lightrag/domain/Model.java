package io.akka.lightrag.domain;

import java.util.List;

/**
 * The records the retrieval pipeline moves around.
 *
 * <p>They are gathered in one file because they are one vocabulary: a reader who needs
 * {@link RetrievedRelation} needs {@link EdgeKey} in the same breath.
 */
public final class Model {

  private Model() {}

  /** Which indexes a query is allowed to reach. Not on its own a branch selector — see
   *  {@code RetrievalPipeline}, where the keyword sets decide alongside it. */
  public enum Mode {
    LOCAL,
    GLOBAL,
    HYBRID,
    MIX
  }

  /** An undirected pair. Two relations naming the same two entities in opposite order
   *  are one relation, so the key is the sorted pair and nothing else. */
  public record EdgeKey(String a, String b) implements Comparable<EdgeKey> {
    public static EdgeKey of(String x, String y) {
      return x.compareTo(y) <= 0 ? new EdgeKey(x, y) : new EdgeKey(y, x);
    }

    /**
     * The form used wherever a relation needs a name: as a record id in the relation
     * index and as an entity id in storage.
     *
     * <p>Both halves are percent-encoded and joined by a comma. An entity name is
     * arbitrary extracted text, so a separator has to be one the names cannot contain —
     * encoding turns a comma inside a name into {@code %2C} and leaves the joining comma
     * as the only one. It also keeps the vertical bar, which the runtime reserves in an
     * entity id, out of the id entirely.
     */
    public String storageId() {
      return encode(a) + "," + encode(b);
    }

    public static EdgeKey fromStorageId(String id) {
      int comma = id.indexOf(',');
      if (comma < 0) {
        throw new IllegalArgumentException(
            "a relation id is two percent-encoded names joined by a comma: " + id);
      }
      return of(decode(id.substring(0, comma)), decode(id.substring(comma + 1)));
    }

    private static String encode(String raw) {
      return java.net.URLEncoder.encode(raw, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String decode(String encoded) {
      return java.net.URLDecoder.decode(encoded, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public int compareTo(EdgeKey o) {
      int c = a.compareTo(o.a);
      return c != 0 ? c : b.compareTo(o.b);
    }

    /** For reading, not for identifying — {@link #storageId()} is the id. */
    @Override
    public String toString() {
      return a + "|" + b;
    }
  }

  public record Node(
      String entityName,
      String entityType,
      String description,
      String filePath,
      List<String> sourceIds) {}

  public record Edge(
      EdgeKey key,
      double weight,
      String description,
      String filePath,
      List<String> sourceIds) {}

  public record Chunk(String chunkId, String content, String filePath) {}

  /** A vector-search result: the stored record's id and its cosine similarity. */
  public record Hit(String id, double score, Long createdAt) {}

  /**
   * An entity as it comes out of one of the two branches.
   *
   * @param rank the node's degree, or null. Only the local branch sets it; an entity
   *     reached through the global branch has none and nothing downstream supplies one.
   */
  public record RetrievedEntity(
      String entityName,
      String entityType,
      String description,
      String filePath,
      List<String> sourceIds,
      Integer rank,
      Long createdAt) {}

  /**
   * A relation as it comes out of one of the two branches.
   *
   * <p>The two branches do not produce interchangeable records. A local relation carries
   * {@code rank} ({@code degree(src) + degree(tgt)}) and no {@code createdAt}; a global
   * one carries the index's {@code createdAt} and no rank. Which branch placed it into
   * the fused list therefore changes what a reader sees, not only where it sits.
   *
   * @param key the sorted pair, which is the identity — two relations naming the same
   *     entities in opposite order are one relation
   * @param src the first endpoint <em>as rendered</em>. The local branch reaches a
   *     relation through the graph and renders it from the sorted pair; the global branch
   *     reaches it through the index, which stores a direction, and renders that. So the
   *     same edge can be printed either way round depending on which branch found it.
   */
  public record RetrievedRelation(
      EdgeKey key,
      String src,
      String tgt,
      double weight,
      String description,
      String filePath,
      List<String> sourceIds,
      Integer rank,
      Long createdAt,
      boolean fromLocalBranch) {

    /** A relation found through the graph: rendered from the sorted pair, ranked by degree. */
    public static RetrievedRelation local(
        EdgeKey key,
        double weight,
        String description,
        String filePath,
        List<String> sourceIds,
        int rank) {
      return new RetrievedRelation(
          key, key.a(), key.b(), weight, description, filePath, sourceIds, rank, null, true);
    }

    /** A relation found through the index: rendered in the stored direction, unranked. */
    public static RetrievedRelation global(
        String src,
        String tgt,
        double weight,
        String description,
        String filePath,
        List<String> sourceIds,
        Long createdAt) {
      return new RetrievedRelation(
          EdgeKey.of(src, tgt), src, tgt, weight, description, filePath, sourceIds, null,
          createdAt, false);
    }
  }

  /** A chunk once it has been attributed to an entity or a relation. */
  public record SelectedChunk(String chunkId, String content, String filePath, String source) {}

  /** A chunk in the final answer, numbered and cited. */
  public record FinalChunk(
      String id, String chunkId, String content, String filePath, String referenceId) {}

  public record Reference(String referenceId, String filePath) {}

  /** What a caller asks for. Every field is copied from the source's {@code QueryParam}
   *  except {@code chunkTokenBudget}, which the source derives from its own prompt
   *  templates and this port takes as an input (SPEC-001 §1). */
  public record QuerySpec(
      Mode mode,
      int topK,
      int chunkTopK,
      int maxEntityTokens,
      int maxRelationTokens,
      int chunkTokenBudget,
      int relatedChunkNumber) {

    public static QuerySpec defaults(Mode mode) {
      return new QuerySpec(mode, 40, 20, 6000, 8000, 12000, 5);
    }
  }

  /** The ordered context a query produces. */
  public record RetrievalResult(
      List<EntityContext> entities,
      List<RelationContext> relations,
      List<FinalChunk> chunks,
      List<Reference> references) {}

  /** The entity projection that reaches a reader. {@code filePath} and {@code createdAt}
   *  are absent by rule, not by omission — SPEC-001 R12. */
  public record EntityContext(String entity, String type, String description) {}

  /** The relation projection that reaches a reader, with the same absences. */
  public record RelationContext(String entity1, String entity2, String description) {}
}
