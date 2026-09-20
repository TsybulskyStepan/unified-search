package com.example.searchapp;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.searchapp.onboarding.service.DocumentService;
import com.example.searchapp.onboarding.service.SummaryWorker;
import com.example.searchapp.search.service.SearchService;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.web.server.LocalServerPort;

class ObservabilityIntegrationTest extends IntegrationTest {
  @LocalServerPort private int port;

  private Logger searchLogger;
  private Logger documentLogger;
  private Logger summaryLogger;
  private ListAppender<ILoggingEvent> searchEvents;
  private ListAppender<ILoggingEvent> documentEvents;
  private ListAppender<ILoggingEvent> summaryEvents;

  @BeforeEach
  void attachAppenders() {
    searchLogger = (Logger) LoggerFactory.getLogger(SearchService.class);
    documentLogger = (Logger) LoggerFactory.getLogger(DocumentService.class);
    summaryLogger = (Logger) LoggerFactory.getLogger(SummaryWorker.class);
    searchEvents = attach(searchLogger);
    documentEvents = attach(documentLogger);
    summaryEvents = attach(summaryLogger);
  }

  @AfterEach
  void detachAppenders() {
    searchLogger.detachAppender(searchEvents);
    documentLogger.detachAppender(documentEvents);
    summaryLogger.detachAppender(summaryEvents);
  }

  @Test
  void searchWriteAndWorkerLogsNeverContainPiiAndMetricsAreInspectable() throws Exception {
    String firstName = "AliciaSecret";
    String lastName = "Investor";
    String email = "alicia.secret@example.test";
    String title = "Private Account Statement";
    String content = "Private statement content for AliciaSecret.";
    String query = "alicia-secret-query";

    String clientId = createClient(firstName, lastName, email);
    String documentId = createDocument(clientId, title, content);
    var searchResponse = get(port, "/search?q=" + query, TEST_API_KEY);
    String requestId = searchResponse.headers().firstValue("X-Request-Id").orElseThrow();
    requestSummary(clientId, documentId);
    awaitStatus(clientId, documentId, "failed");

    ILoggingEvent auditLine =
        searchEvents.list.stream()
            .filter(event -> event.getFormattedMessage().startsWith("Search audit"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no search audit line was captured"));
    assertThat(auditLine.getFormattedMessage())
        .contains(
            "request_id=" + requestId,
            "query_length=" + query.length(),
            "lexical_hits=",
            "semantic_hits=",
            "returned=",
            "lexical_ms=",
            "embed_query_ms=",
            "semantic_ms=",
            "total_ms=");
    assertThat(documentEvents.list)
        .anySatisfy(
            event ->
                assertThat(event.getFormattedMessage())
                    .startsWith("Document indexed document_id="));
    assertThat(summaryEvents.list)
        .anySatisfy(event -> assertThat(event.getFormattedMessage()).startsWith("Summary outcome"));

    List<String> pii = List.of(firstName, lastName, email, title, content, query);
    List<ILoggingEvent> events =
        List.of(searchEvents.list, documentEvents.list, summaryEvents.list).stream()
            .flatMap(List::stream)
            .toList();
    assertThat(events).isNotEmpty();
    for (ILoggingEvent event : events) {
      for (String sensitiveValue : pii) {
        assertThat(event.getFormattedMessage()).doesNotContain(sensitiveValue);
      }
    }

    var metrics = get(port, "/metrics", TEST_API_KEY);
    assertThat(metrics.statusCode()).isEqualTo(200);
    assertThat(metrics.body())
        .contains(
            "search.lexical",
            "search.embed_query",
            "search.semantic",
            "search.total",
            "document.embed",
            "summary.call",
            "summary.outcome");

    var summaryOutcomes = get(port, "/metrics/summary.outcome?tag=status:failed", TEST_API_KEY);
    assertThat(summaryOutcomes.statusCode()).isEqualTo(200);
    assertThat(summaryOutcomes.body()).contains("COUNT");
  }

  private String createClient(String firstName, String lastName, String email) throws Exception {
    var response =
        post(
            port,
            "/clients",
            TEST_API_KEY,
            "{\"first_name\":\"%s\",\"last_name\":\"%s\",\"email\":\"%s\"}"
                .formatted(firstName, lastName, email));
    assertThat(response.statusCode()).isEqualTo(201);
    return extractId(response.body());
  }

  private String createDocument(String clientId, String title, String content) throws Exception {
    var response =
        post(
            port,
            "/clients/" + clientId + "/documents",
            TEST_API_KEY,
            "{\"title\":\"%s\",\"content\":\"%s\"}".formatted(title, content));
    assertThat(response.statusCode()).isEqualTo(201);
    return extractId(response.body());
  }

  private void requestSummary(String clientId, String documentId) throws Exception {
    var response =
        post(
            port,
            "/clients/" + clientId + "/documents/" + documentId + "/summary",
            TEST_API_KEY,
            "");
    assertThat(response.statusCode()).isEqualTo(202);
  }

  private void awaitStatus(String clientId, String documentId, String expected) throws Exception {
    Instant deadline = Instant.now().plusSeconds(5);
    HttpResponse<String> response = null;
    while (Instant.now().isBefore(deadline)) {
      response = get(port, "/clients/" + clientId + "/documents/" + documentId, TEST_API_KEY);
      if (response.body().contains("\"summary_status\":\"" + expected + "\"")) {
        return;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("summary_status never reached '" + expected + "': " + response.body());
  }

  private static ListAppender<ILoggingEvent> attach(Logger logger) {
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    return appender;
  }

  private static String extractId(String body) {
    var matcher = Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\"").matcher(body);
    if (!matcher.find()) {
      throw new AssertionError("response did not contain an id: " + body);
    }
    return matcher.group(1);
  }
}
