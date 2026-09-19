package com.example.searchapp.onboarding.service;

import com.example.searchapp.onboarding.exception.PermanentSummarizationException;
import org.springframework.stereotype.Component;

/**
 * The only {@link Summarizer} bean until ticket 11's {@code GeminiSummarizer} replaces it — keeps
 * the app runnable in the meantime (§15).
 */
@Component
class DisabledSummarizer implements Summarizer {
  @Override
  public String summarize(String title, String content) {
    throw new PermanentSummarizationException("No summarizer is configured");
  }
}
