package com.example.searchapp.onboarding.exception;

/**
 * A {@code Summarizer} call failed in a way that is expected to succeed on retry — rate limits,
 * server errors, timeouts (system-design §7.2, ticket 11). The claiming row keeps its incremented
 * attempt count and stays {@code pending}; it becomes claimable again once its lease expires, and
 * is only moved to {@code failed} once attempts are exhausted (§7.2 sweep).
 *
 * <p>Lives here rather than beside {@code Summarizer} in {@code onboarding.service}: unlike {@code
 * ClientNotFoundException} and its neighbours, it never reaches {@code OnboardingExceptionHandler}
 * — {@code SummaryWorker} is its only catcher — but it is still the module's exception type for a
 * failure, which is what this package groups.
 */
public class TransientSummarizationException extends RuntimeException {
  public TransientSummarizationException(String message) {
    super(message);
  }

  public TransientSummarizationException(String message, Throwable cause) {
    super(message, cause);
  }
}
