package com.example.searchapp.onboarding.service.summarizer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the one {@link Summarizer} bean (§7.3): {@link GeminiSummarizer} when {@code
 * GEMINI_API_KEY} is set to something non-blank, {@link DisabledSummarizer} otherwise. No key, a
 * blank key and a key of only whitespace all take the disabled path — the same one a bad or revoked
 * key reaches once {@link GeminiSummarizer} actually calls Gemini and gets rejected.
 *
 * <p>{@code SUMMARY_MODEL} is read the same way, with its own default, so a retired model id can be
 * overridden without a rebuild (§11.2).
 */
@Configuration
public class SummarizerConfig {
  static final String DEFAULT_MODEL = "gemini-3.6-flash";

  @Bean
  public Summarizer summarizer(
      @Value("${GEMINI_API_KEY:}") String apiKey, @Value("${SUMMARY_MODEL:}") String model) {
    if (apiKey == null || apiKey.isBlank()) {
      return new DisabledSummarizer();
    }
    return new GeminiSummarizer(apiKey, model == null || model.isBlank() ? DEFAULT_MODEL : model);
  }
}
