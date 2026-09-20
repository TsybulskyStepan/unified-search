package com.example.searchapp.onboarding.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.searchapp.onboarding.exception.PermanentSummarizationException;
import org.junit.jupiter.api.Test;

/** The stand-in {@code SummarizerConfig} returns when no {@code GEMINI_API_KEY} is set (§7.3). */
class DisabledSummarizerTest {
  @Test
  void everyCallFailsPermanently() {
    assertThatThrownBy(() -> new DisabledSummarizer().summarize("title", "content"))
        .isInstanceOf(PermanentSummarizationException.class);
  }
}
