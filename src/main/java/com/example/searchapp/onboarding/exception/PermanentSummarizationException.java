package com.example.searchapp.onboarding.exception;

/**
 * A {@code Summarizer} call failed in a way retrying cannot fix — bad auth, an invalid request, or
 * the summarizer being unavailable at all (system-design §7.2, §7.3). The claiming row is moved
 * straight to {@code failed}, without waiting for the attempt limit.
 *
 * <p>Lives here rather than beside {@code Summarizer} in {@code onboarding.service}: see {@link
 * TransientSummarizationException}'s note on why.
 */
public class PermanentSummarizationException extends RuntimeException {
  public PermanentSummarizationException(String message) {
    super(message);
  }

  public PermanentSummarizationException(String message, Throwable cause) {
    super(message, cause);
  }
}
