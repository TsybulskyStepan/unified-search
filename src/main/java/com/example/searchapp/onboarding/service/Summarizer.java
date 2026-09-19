package com.example.searchapp.onboarding.service;

import com.example.searchapp.onboarding.exception.PermanentSummarizationException;
import com.example.searchapp.onboarding.exception.TransientSummarizationException;

/**
 * {@code GeminiSummarizer} (ticket 11) is the only production implementation; {@link
 * DisabledSummarizer} stands in until then, and tests supply their own double. Throws {@link
 * TransientSummarizationException} or {@link PermanentSummarizationException} on failure.
 */
public interface Summarizer {
  String summarize(String title, String content);
}
