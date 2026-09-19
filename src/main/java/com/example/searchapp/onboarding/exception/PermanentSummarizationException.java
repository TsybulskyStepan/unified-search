package com.example.searchapp.onboarding.exception;

/**
 * A {@code Summarizer} call failed in a way retrying cannot fix (§7.2, §7.3). See {@link
 * TransientSummarizationException} for why this lives here rather than beside {@code Summarizer}.
 */
public class PermanentSummarizationException extends RuntimeException {
  public PermanentSummarizationException(String message) {
    super(message);
  }

  public PermanentSummarizationException(String message, Throwable cause) {
    super(message, cause);
  }
}
