package com.example.searchapp.onboarding.service;

import com.example.searchapp.onboarding.exception.PermanentSummarizationException;
import org.springframework.stereotype.Component;

/**
 * The only {@link Summarizer} bean until ticket 11 adds {@code GeminiSummarizer}. It fails every
 * call permanently, so a requested summary reaches {@code failed} within one nudge instead of the
 * worker having no summarizer bean to inject at all (§15: every ticket leaves a runnable system).
 * This is deliberately the same shape {@code GeminiSummarizer} will use for its own "no API key"
 * path (§7.3) — ticket 11 replaces this class rather than adding a parallel one.
 */
@Component
class DisabledSummarizer implements Summarizer {
  @Override
  public String summarize(String title, String content) {
    throw new PermanentSummarizationException("No summarizer is configured");
  }
}
