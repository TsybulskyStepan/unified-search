package com.example.searchapp.onboarding.service;

import com.example.searchapp.onboarding.exception.PermanentSummarizationException;

/**
 * {@link SummarizerConfig} returns this in place of {@link GeminiSummarizer} when {@code
 * GEMINI_API_KEY} is absent — the default, and what every reviewer running this locally will have
 * (§7.3). Every requested summary reaches {@code failed} within one nudge, by the same code path a
 * bad or revoked key would take once {@link GeminiSummarizer} rejects it.
 */
class DisabledSummarizer implements Summarizer {
  @Override
  public String summarize(String title, String content) {
    throw new PermanentSummarizationException("No summarizer is configured");
  }
}
