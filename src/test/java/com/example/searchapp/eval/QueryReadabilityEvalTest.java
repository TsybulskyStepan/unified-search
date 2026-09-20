package com.example.searchapp.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.searchapp.onboarding.seed.DemoCorpus;
import com.example.searchapp.shared.embedding.Embedder;
import org.junit.jupiter.api.Test;

/**
 * The readability gate (§6.3) rejects text the model cannot read as words. It must reject every
 * {@code gibberish} query and never a query whose documents the semantic signal has to recall, so a
 * threshold that drifts to either side fails here, not in production.
 */
class QueryReadabilityEvalTest {
  private static final Embedder embedder = new Embedder();

  @Test
  void rejectsEveryGibberishQueryAndNoQueryWhoseDocumentsSemanticRetrievalMustRecall() {
    DemoCorpus corpus = EvalCorpusLoader.corpus();
    EvalQueries queries = EvalCorpusLoader.queries();

    int gibberish = 0;
    int recalled = 0;
    for (EvalQueries.EvalQuery query : queries.queries()) {
      boolean readable = embedder.embedQuery(query.query()).readable();
      if (query.shape() == EvalQueries.Shape.GIBBERISH) {
        assertThat(readable).as("gibberish '%s' is unreadable", query.query()).isFalse();
        gibberish++;
      } else if (!queries.semanticPositives(query, corpus).isEmpty()) {
        assertThat(readable).as("'%s' is readable", query.query()).isTrue();
        recalled++;
      }
    }

    assertThat(gibberish).isPositive();
    assertThat(recalled).isPositive();
  }
}
