package com.example.searchapp;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.searchapp.shared.web.RequestIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Guards the no-PII-in-logs invariant with an actual assertion on a log line, not just on the
 * {@code X-Request-Id} response header — CLAUDE.md requires each invariant to be enforced by a test
 * once its code exists, and until this test existed, nothing checked that the request id reaches
 * the log line at all.
 */
class RequestIdLoggingIntegrationTest extends IntegrationTest {
  @LocalServerPort private int port;

  private Logger requestIdLogger;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void attachAppender() {
    requestIdLogger = (Logger) LoggerFactory.getLogger(RequestIdFilter.class);
    appender = new ListAppender<>();
    appender.start();
    requestIdLogger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    requestIdLogger.detachAppender(appender);
  }

  @Test
  void requestCompletedLogLineCarriesTheSameRequestIdAsTheResponseHeader() throws Exception {
    var response = get(port, "/health");
    String requestId = response.headers().firstValue("X-Request-Id").orElseThrow();

    ILoggingEvent event =
        appender.list.stream()
            .filter(e -> e.getFormattedMessage().startsWith("request completed"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no request-completed log line was captured"));

    assertThat(event.getMDCPropertyMap()).containsEntry("request_id", requestId);
  }
}
