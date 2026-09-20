package com.example.searchapp.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.searchapp.onboarding.seed.DemoCorpus;
import com.example.searchapp.onboarding.service.Chunk;
import com.example.searchapp.onboarding.service.Chunker;
import com.example.searchapp.shared.embedding.Embedder;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guards the chunk geometry in {@link Chunker} against the model's real input limit, measured
 * directly through {@link Embedder#tokenCount} — the model's own tokenizer, not an estimate.
 *
 * <p>The bundled all-MiniLM-L6-v2 tokenizer truncates silently past 126 word pieces (see {@code
 * Chunker}'s class javadoc); this test proves every chunk actually produced from the eval/seed
 * corpus, title included, stays under that ceiling with margin, on dense KYC prose as well as plain
 * narrative.
 */
class EmbeddingWordPieceBoundTest {
  private static final int MEASURED_WORD_PIECE_CEILING = 126;
  private static final Embedder embedder = new Embedder();

  @Test
  void everyCorpusChunkStaysUnderTheMeasuredWordPieceCeiling() {
    DemoCorpus corpus = EvalCorpusLoader.corpus();
    int checked = 0;
    int maxTokenCount = 0;

    for (DemoCorpus.DemoClient client : corpus.clients()) {
      for (DemoCorpus.DemoDocument document : client.documents()) {
        List<Chunk> chunks = Chunker.split(document.content());
        assertThat(chunks).as("document '%s' must chunk", document.title()).isNotEmpty();

        for (Chunk chunk : chunks) {
          String embeddingInput =
              Chunker.embeddingInput(document.title(), document.content(), chunk);
          int tokenCount = embedder.tokenCount(embeddingInput);

          assertThat(tokenCount)
              .as(
                  "'%s' chunk #%d embedding input must stay under the measured word-piece"
                      + " ceiling",
                  document.title(), chunk.ordinal())
              .isLessThanOrEqualTo(MEASURED_WORD_PIECE_CEILING);

          maxTokenCount = Math.max(maxTokenCount, tokenCount);
          checked++;
        }
      }
    }

    assertThat(checked).isPositive();
    System.out.printf(
        "checked %d chunks; max word-piece count observed: %d (ceiling %d)%n",
        checked, maxTokenCount, MEASURED_WORD_PIECE_CEILING);
  }
}
