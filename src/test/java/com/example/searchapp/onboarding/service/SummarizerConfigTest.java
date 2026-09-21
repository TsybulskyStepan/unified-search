package com.example.searchapp.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.searchapp.onboarding.service.summarizer.DisabledSummarizer;
import com.example.searchapp.onboarding.service.summarizer.GeminiSummarizer;
import com.example.searchapp.onboarding.service.summarizer.SummarizerConfig;
import org.junit.jupiter.api.Test;

/**
 * The switch between {@link DisabledSummarizer} and {@link GeminiSummarizer} (§7.3): both a "no
 * key", a "blank key" and a "key made of only whitespace" case go the same degraded path, since a
 * reviewer's shell or {@code .env} could plausibly produce any of them.
 */
class SummarizerConfigTest {
  private final SummarizerConfig config = new SummarizerConfig();

  @Test
  void aMissingOrBlankKeyProducesTheDisabledSummarizer() {
    assertThat(config.summarizer(null, null)).isInstanceOf(DisabledSummarizer.class);
    assertThat(config.summarizer("", null)).isInstanceOf(DisabledSummarizer.class);
    assertThat(config.summarizer("   ", null)).isInstanceOf(DisabledSummarizer.class);
  }

  @Test
  void anyNonBlankKeyProducesTheGeminiSummarizer() {
    assertThat(config.summarizer("a-real-looking-key", null)).isInstanceOf(GeminiSummarizer.class);
    assertThat(config.summarizer("a-real-looking-key", "custom-model"))
        .isInstanceOf(GeminiSummarizer.class);
  }
}
