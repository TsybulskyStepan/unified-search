package com.example.searchapp.onboarding.service.summarizer;

import com.example.searchapp.onboarding.exception.PermanentSummarizationException;
import com.example.searchapp.onboarding.exception.TransientSummarizationException;

/**
 * {@link GeminiSummarizer} is the only production implementation; {@link SummarizerConfig} falls
 * back to {@link DisabledSummarizer} when {@code GEMINI_API_KEY} is absent, and tests supply their
 * own double. Throws {@link TransientSummarizationException} or {@link
 * PermanentSummarizationException} on failure.
 */
public interface Summarizer {
  String summarize(String title, String content);
}
