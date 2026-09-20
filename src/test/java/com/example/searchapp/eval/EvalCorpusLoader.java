package com.example.searchapp.eval;

import com.example.searchapp.onboarding.seed.DemoCorpus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * Reads the eval-set JSON fixtures shared by the corpus/query-based tests (§12.3). {@code
 * corpus.json} is read from {@code seed/}, not a test-owned copy: it is the same file {@link
 * com.example.searchapp.onboarding.seed.DemoSeeder} loads in production (§11.3), on the test
 * classpath because it ships in {@code src/main/resources}.
 */
public final class EvalCorpusLoader {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private EvalCorpusLoader() {}

  public static DemoCorpus corpus() {
    return read("/seed/corpus.json", DemoCorpus.class);
  }

  public static EvalQueries queries() {
    return read("/eval/queries.json", EvalQueries.class);
  }

  /**
   * The seed corpus's expected document type, keyed by title (§11.1, ticket 15): {@code
   * DocumentClassifierTest} checks every seed document against this checked-in expectation. Keyed
   * by title rather than one entry per document because a handful of titles repeat across clients
   * and must always classify the same way — classification depends only on title and content, never
   * on who owns the document.
   */
  public static Map<String, String> classification() {
    try (InputStream in = EvalCorpusLoader.class.getResourceAsStream("/eval/classification.json")) {
      if (in == null) {
        throw new IllegalStateException(
            "Missing eval fixture on classpath: /eval/classification.json");
      }
      return MAPPER.readValue(in, new TypeReference<Map<String, String>>() {});
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read eval fixture: /eval/classification.json", e);
    }
  }

  private static <T> T read(String resource, Class<T> type) {
    try (InputStream in = EvalCorpusLoader.class.getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("Missing eval fixture on classpath: " + resource);
      }
      return MAPPER.readValue(in, type);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read eval fixture: " + resource, e);
    }
  }
}
