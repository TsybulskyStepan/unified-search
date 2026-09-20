package com.example.searchapp.eval;

import com.example.searchapp.onboarding.seed.DemoCorpus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

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
