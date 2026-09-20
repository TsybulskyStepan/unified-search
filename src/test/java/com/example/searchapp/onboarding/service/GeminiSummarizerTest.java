package com.example.searchapp.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.searchapp.onboarding.exception.PermanentSummarizationException;
import com.example.searchapp.onboarding.exception.TransientSummarizationException;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.ClientException;
import com.google.genai.errors.ServerException;
import com.google.genai.types.GenerateContentConfig;
import org.junit.jupiter.api.Test;

/**
 * Unit-tests the two things that don't need a live Gemini call: how SDK errors are classified
 * (ticket 11 AC "rate limits, server errors and timeouts are transient; authentication and
 * invalid-request failures are permanent") and the shape of the request config (bounded timeout,
 * bounded output, document text kept out of the instructions). The success and end-to-end
 * degradation paths are covered by {@code SummarizerConfigTest}, {@code DisabledSummarizerTest} and
 * ticket 10's {@code SummaryApiIntegrationTest} against the stub; a real key is required to
 * exercise an actual model call, which these tests deliberately don't need.
 */
class GeminiSummarizerTest {
  private final GeminiSummarizer summarizer = new GeminiSummarizer("test-key", "gemini-2.5-flash");

  @Test
  void authenticationFailuresArePermanent() {
    assertThat(GeminiSummarizer.classify(new ClientException(401, "UNAUTHENTICATED", "no key")))
        .isInstanceOf(PermanentSummarizationException.class);
    assertThat(GeminiSummarizer.classify(new ClientException(403, "PERMISSION_DENIED", "revoked")))
        .isInstanceOf(PermanentSummarizationException.class);
  }

  @Test
  void invalidRequestsArePermanent() {
    assertThat(GeminiSummarizer.classify(new ClientException(400, "INVALID_ARGUMENT", "bad")))
        .isInstanceOf(PermanentSummarizationException.class);
  }

  @Test
  void rateLimitsAreTransientDespiteBeingA4xx() {
    assertThat(
            GeminiSummarizer.classify(new ClientException(429, "RESOURCE_EXHAUSTED", "slow down")))
        .isInstanceOf(TransientSummarizationException.class);
  }

  @Test
  void serverErrorsAreTransient() {
    assertThat(GeminiSummarizer.classify(new ServerException(503, "UNAVAILABLE", "down")))
        .isInstanceOf(TransientSummarizationException.class);
  }

  @Test
  void unrecognisedApiErrorsDefaultToTransient() {
    assertThat(GeminiSummarizer.classify(new ApiException(0, "UNKNOWN", "?")))
        .isInstanceOf(TransientSummarizationException.class);
  }

  @Test
  void theCallIsBoundedComfortablyUnderTheTwoMinuteClaimLease() {
    GenerateContentConfig config = summarizer.config();
    assertThat(config.httpOptions()).isPresent();
    assertThat(config.httpOptions().get().timeout()).isPresent();
    // §7.2's lease is 2 minutes (120_000 ms); "comfortably shorter" means well under that, not
    // merely under it.
    assertThat(config.httpOptions().get().timeout().get()).isLessThan(60_000);
  }

  @Test
  void outputLengthIsBounded() {
    assertThat(summarizer.config().maxOutputTokens()).contains(200);
  }

  @Test
  void theSystemInstructionTreatsDocumentTextAsDataNotInstructions() {
    String instruction = summarizer.config().systemInstruction().orElseThrow().text();
    assertThat(instruction).containsIgnoringCase("data");
    assertThat(instruction).containsIgnoringCase("never instructions");
  }
}
