package com.example.searchapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.searchapp.onboarding.exception.PermanentSummarizationException;
import com.example.searchapp.onboarding.exception.TransientSummarizationException;
import com.example.searchapp.onboarding.service.StubSummarizer;
import com.example.searchapp.onboarding.service.SummaryWorker;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * §7.2's lifecycle end to end, against {@link StubSummarizer} — "build this against a test double"
 * (ticket 10); the real model arrives in ticket 11. "Searchable throughout" (§7.3) is verified
 * through {@code GET} on the document: {@code main} does not yet carry document semantic search
 * (ticket 07, in flight separately), so the document-fetch path is the available proxy for "the row
 * is untouched and still there" in this module's own tests.
 */
@Import(SummaryApiIntegrationTest.SummarizerTestConfig.class)
class SummaryApiIntegrationTest extends IntegrationTest {
  @LocalServerPort private int port;
  @Autowired private StubSummarizer summarizer;
  @Autowired private SummaryWorker summaryWorker;
  @Autowired private JdbcClient jdbcClient;

  @Test
  void requestingASummaryMovesNoneToPendingThenReadyAndStaysFetchableThroughout() throws Exception {
    summarizer.reset();
    String clientId = createClient("Jane", "Summary", "jane.summary@example.com");
    String documentId = createDocument(clientId, "Bill", "Account 123, due 15 June.");

    var fetchedBeforeRequest = getDocument(clientId, documentId);
    assertThat(fetchedBeforeRequest.body()).contains("\"summary_status\":\"none\"");

    var requested = requestSummary(clientId, documentId);
    assertThat(requested.statusCode()).isEqualTo(202);
    assertThat(requested.body()).contains("\"summary_status\":\"pending\"");

    String ready = awaitStatus(clientId, documentId, "ready");
    assertThat(ready).contains("\"summary\":\"" + StubSummarizer.DEFAULT_SUMMARY + "\"");
    assertThat(summarizer.callCount()).isEqualTo(1);
  }

  @Test
  void repeatedRequestsAreNoOpsAndNeverDoubleCallTheSummarizer() throws Exception {
    summarizer.reset();
    String clientId = createClient("Repeat", "Request", "repeat.request@example.com");
    String documentId = createDocument(clientId, "Bill", "Account 456");

    // Holds the worker mid-call, so the second request below is verifiably racing a document
    // that is still pending rather than one that already raced ahead to ready (the stub is
    // otherwise fast enough that the async nudge can beat a second HTTP call to the server).
    summarizer.pauseNextCall();
    var first = requestSummary(clientId, documentId);
    assertThat(first.statusCode()).isEqualTo(202);
    awaitCallCount(1);

    var second = requestSummary(clientId, documentId);
    assertThat(second.statusCode()).isEqualTo(202);
    assertThat(second.body()).contains("\"summary_status\":\"pending\"");
    assertThat(summarizer.callCount()).isEqualTo(1);

    summarizer.release();
    awaitStatus(clientId, documentId, "ready");
    assertThat(summarizer.callCount()).isEqualTo(1);

    var third = requestSummary(clientId, documentId);
    assertThat(third.statusCode()).isEqualTo(200);
    assertThat(third.body()).contains("\"summary_status\":\"ready\"");
    assertThat(summarizer.callCount()).isEqualTo(1);
  }

  @Test
  void twoConcurrentInitialRequestsBothReport202NeverAStaleNone() throws Exception {
    // Regression for /plannotator-review [P1]: the loser of a genuine race on the none->pending
    // transition must report the row's real current state, not a snapshot read before either
    // request's update ran (which would still read "none" and answer 200 instead of 202).
    summarizer.reset();
    String clientId = createClient("Concurrent", "Request", "concurrent.request@example.com");
    String documentId = createDocument(clientId, "Bill", "Account 222");

    // Pauses the winner's nudge mid-call, so by the time both HTTP responses are asserted the row
    // is still genuinely "pending" rather than having already raced ahead to "ready".
    summarizer.pauseNextCall();

    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch go = new CountDownLatch(1);
    List<Future<HttpResponse<String>>> responses = new ArrayList<>();
    try {
      for (int i = 0; i < 2; i++) {
        responses.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  go.await();
                  return requestSummary(clientId, documentId);
                }));
      }
      ready.await();
      go.countDown();
    } finally {
      pool.shutdown();
      assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    for (Future<HttpResponse<String>> response : responses) {
      assertThat(response.get().statusCode()).isEqualTo(202);
      assertThat(response.get().body()).contains("\"summary_status\":\"pending\"");
    }
    assertThat(summarizer.callCount()).isEqualTo(1);

    summarizer.release();
    awaitStatus(clientId, documentId, "ready");
  }

  @Test
  void gettingADocumentRepeatedlyNeverTriggersGeneration() throws Exception {
    summarizer.reset();
    String clientId = createClient("Get", "Only", "get.only@example.com");
    String documentId = createDocument(clientId, "Bill", "Account 789");

    for (int i = 0; i < 5; i++) {
      var fetched = getDocument(clientId, documentId);
      assertThat(fetched.statusCode()).isEqualTo(200);
      assertThat(fetched.body()).contains("\"summary_status\":\"none\"");
    }
    assertThat(summarizer.callCount()).isZero();
  }

  @Test
  void retryAfterFailureResetsAttemptsAndCanThenSucceed() throws Exception {
    summarizer.reset();
    String clientId = createClient("Retry", "AfterFail", "retry.afterfail@example.com");
    String documentId = createDocument(clientId, "Bill", "Account 000");

    summarizer.queueFailure(new PermanentSummarizationException("bad credentials"));
    requestSummary(clientId, documentId);
    awaitStatus(clientId, documentId, "failed");
    assertThat(summarizer.callCount()).isEqualTo(1);

    // Attempts were reset to 0 by the retry's UPDATE before the worker ever gets a row to claim,
    // but the async nudge can increment them to 1 (claim time) at any point after the 202 comes
    // back — asserting 0 right after the response races that claim. Pausing lets the test observe
    // the deterministic fact instead: the claim SQL requires attempts < 3, so a successful claim
    // to exactly 1 is only possible because the reset actually happened (had it not, the exhausted
    // row from the failure above would still read 3, and claimPending would find nothing at all).
    summarizer.pauseNextCall();
    var retry = requestSummary(clientId, documentId);
    assertThat(retry.statusCode()).isEqualTo(202);
    assertThat(retry.body()).contains("\"summary_status\":\"pending\"");
    awaitCallCount(2);
    assertThat(attemptsFor(documentId)).isEqualTo(1);

    summarizer.release();
    String ready = awaitStatus(clientId, documentId, "ready");
    assertThat(ready).contains("\"summary\":\"" + StubSummarizer.DEFAULT_SUMMARY + "\"");
    assertThat(summarizer.callCount()).isEqualTo(2);
  }

  @Test
  void transientFailuresAreRetriedUntilTheAttemptLimitThenFail() throws Exception {
    summarizer.reset();
    String clientId = createClient("Exhaust", "Attempts", "exhaust.attempts@example.com");
    String documentId = createDocument(clientId, "Bill", "Account 111");
    UUID id = UUID.fromString(documentId);

    summarizer.queueFailure(new TransientSummarizationException("timeout"));
    requestSummary(clientId, documentId);
    awaitAttempts(id, 1);
    assertThat(statusFor(id)).isEqualTo("pending");

    for (int attempt = 2; attempt <= 3; attempt++) {
      summarizer.queueFailure(new TransientSummarizationException("timeout"));
      expireLease(id);
      summaryWorker.runOnce();
      awaitAttempts(id, attempt);
      assertThat(statusFor(id)).isEqualTo("pending");
    }

    // Attempts are exhausted but the lease from the 3rd claim is still active: the row is not
    // claimable, and not yet failed, until that lease also expires (§7.2's claim guard).
    expireLease(id);
    summaryWorker.runOnce();

    assertThat(statusFor(id)).isEqualTo("failed");
    assertThat(summarizer.callCount()).isEqualTo(3);

    var stillFetchable = getDocument(clientId, documentId);
    assertThat(stillFetchable.statusCode()).isEqualTo(200);
    assertThat(stillFetchable.body()).contains("\"summary_status\":\"failed\"");
  }

  private String createClient(String firstName, String lastName, String email) throws Exception {
    var response =
        post(
            port,
            "/clients",
            TEST_API_KEY,
            "{\"first_name\":\"%s\",\"last_name\":\"%s\",\"email\":\"%s\"}"
                .formatted(firstName, lastName, email));
    return extractId(response.body());
  }

  private String createDocument(String clientId, String title, String content) throws Exception {
    var response =
        post(
            port,
            "/clients/" + clientId + "/documents",
            TEST_API_KEY,
            "{\"title\":\"%s\",\"content\":\"%s\"}".formatted(title, content));
    return extractId(response.body());
  }

  private HttpResponse<String> requestSummary(String clientId, String documentId) throws Exception {
    return post(
        port, "/clients/" + clientId + "/documents/" + documentId + "/summary", TEST_API_KEY, "");
  }

  private HttpResponse<String> getDocument(String clientId, String documentId) throws Exception {
    return get(port, "/clients/" + clientId + "/documents/" + documentId, TEST_API_KEY);
  }

  private String awaitStatus(String clientId, String documentId, String expected) throws Exception {
    Instant deadline = Instant.now().plusSeconds(5);
    String body = null;
    while (Instant.now().isBefore(deadline)) {
      body = getDocument(clientId, documentId).body();
      if (body.contains("\"summary_status\":\"" + expected + "\"")) {
        return body;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("summary_status never reached '" + expected + "', last body: " + body);
  }

  private void awaitCallCount(int expected) throws InterruptedException {
    Instant deadline = Instant.now().plusSeconds(5);
    while (Instant.now().isBefore(deadline)) {
      if (summarizer.callCount() >= expected) {
        return;
      }
      Thread.sleep(20);
    }
    throw new AssertionError(
        "summarizer call count never reached " + expected + ", was " + summarizer.callCount());
  }

  private void awaitAttempts(UUID documentId, int expected) throws InterruptedException {
    Instant deadline = Instant.now().plusSeconds(5);
    int attempts = -1;
    while (Instant.now().isBefore(deadline)) {
      attempts = attemptsFor(documentId.toString());
      if (attempts >= expected) {
        return;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("attempts never reached " + expected + ", last was " + attempts);
  }

  private int attemptsFor(String documentId) {
    return jdbcClient
        .sql("SELECT summary_attempts FROM document WHERE id = :id")
        .param("id", UUID.fromString(documentId))
        .query(Integer.class)
        .single();
  }

  private String statusFor(UUID documentId) {
    return jdbcClient
        .sql("SELECT summary_status FROM document WHERE id = :id")
        .param("id", documentId)
        .query(String.class)
        .single();
  }

  private void expireLease(UUID documentId) {
    jdbcClient
        .sql("UPDATE document SET summary_lease_until = now() - interval '1 minute' WHERE id = :id")
        .param("id", documentId)
        .update();
  }

  private static String extractId(String json) {
    int start = json.indexOf("\"id\":\"") + 6;
    int end = json.indexOf('"', start);
    return json.substring(start, end);
  }

  @TestConfiguration
  static class SummarizerTestConfig {
    @Bean
    @Primary
    StubSummarizer stubSummarizer() {
      return new StubSummarizer();
    }
  }
}
