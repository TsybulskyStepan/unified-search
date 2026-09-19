package com.example.searchapp.onboarding.exception;

/**
 * A {@code Summarizer} call failed in a retryable way (§7.2). Unlike {@code
 * ClientNotFoundException} and its neighbours, only {@code SummaryWorker} catches this — it never
 * reaches {@code OnboardingExceptionHandler}.
 */
public class TransientSummarizationException extends RuntimeException {
  public TransientSummarizationException(String message) {
    super(message);
  }

  public TransientSummarizationException(String message, Throwable cause) {
    super(message, cause);
  }
}
