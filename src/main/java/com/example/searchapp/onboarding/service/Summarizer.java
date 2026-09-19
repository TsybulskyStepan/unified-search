package com.example.searchapp.onboarding.service;

import com.example.searchapp.onboarding.exception.PermanentSummarizationException;
import com.example.searchapp.onboarding.exception.TransientSummarizationException;

/**
 * Produces a short summary of a document's content (§7.4). The only production implementation is
 * {@code GeminiSummarizer} (ticket 11); {@link DisabledSummarizer} stands in until that lands, and
 * tests supply their own double.
 *
 * <p>A call either returns a summary or throws {@link TransientSummarizationException} / {@link
 * PermanentSummarizationException} — {@link SummaryWorker} decides how to react to each.
 */
public interface Summarizer {
  String summarize(String title, String content);
}
