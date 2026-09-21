package com.example.searchapp.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.searchapp.search.planner.ClientMention;
import com.example.searchapp.search.planner.QueryPlan;
import com.example.searchapp.search.service.SearchTelemetry.Recording;
import com.example.searchapp.search.service.SearchTelemetry.Stage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class SearchTelemetryTest {
  private static final ClientMention JOHN =
      new ClientMention(UUID.fromString("00000000-0000-0000-0000-000000000001"), "name", 1.0);

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final SearchTelemetry telemetry = new SearchTelemetry(registry);
  private final Logger logger = (Logger) LoggerFactory.getLogger(SearchTelemetry.class);
  private final ListAppender<ILoggingEvent> events = new ListAppender<>();

  @BeforeEach
  void captureLogs() {
    events.start();
    logger.addAppender(events);
    MDC.put("request_id", "req-1");
  }

  @AfterEach
  void releaseLogs() {
    logger.detachAppender(events);
    MDC.clear();
  }

  @Test
  void emitsOneAuditLineCarryingEveryFieldAndNeverTheQueryText() {
    Recording recording = telemetry.start();
    recording.timed(Stage.PLAN, () -> "planned");
    recording.planned(
        new QueryPlan(
            "john bill",
            List.of(JOHN),
            "bill",
            Set.of("utility_bill"),
            Set.of("proof_of_address")));
    recording.timed(Stage.CLIENTS, () -> "clients");
    recording.retrieved(
        new RetrievalMeasurements(1_000_000, 2_000_000, 3_000_000, 4_000_000, 5, 6, 7));
    recording.clientHits(2);

    recording.finish(9, 3);

    assertThat(events.list).hasSize(1);
    assertThat(events.list.getFirst().getFormattedMessage())
        .startsWith("Search audit")
        .contains(
            "request_id=req-1",
            "query_length=9",
            "plan_shape=compound",
            "intent_count=2",
            "mention_present=true",
            "client_hits=2",
            "label_hits=5",
            "lexical_hits=6",
            "semantic_hits=7",
            "returned=3",
            "plan_ms=",
            "clients_ms=",
            "label_ms=1",
            "lexical_ms=2",
            "embed_query_ms=3",
            "semantic_ms=4",
            "total_ms=")
        .doesNotContain("john", "bill");
  }

  @Test
  void recordsEverySearchTimerOnceWhenEveryStageRan() {
    Recording recording = telemetry.start();
    recording.timed(Stage.PLAN, () -> "planned");
    recording.timed(Stage.CLIENTS, () -> "clients");
    recording.retrieved(new RetrievalMeasurements(1, 2, 3, 4, 0, 0, 0));

    recording.finish(1, 0);

    for (String name :
        List.of(
            "search.plan",
            "search.clients",
            "search.label",
            "search.lexical",
            "search.embed_query",
            "search.semantic",
            "search.total")) {
      assertThat(registry.timer(name).count()).as(name).isEqualTo(1);
    }
  }

  @Test
  void recordsNoRetrievalTimersWhenRetrievalWasSkipped() {
    Recording recording = telemetry.start();
    recording.timed(Stage.PLAN, () -> "planned");
    recording.clientHits(1);

    recording.finish(4, 1);

    assertThat(registry.timer("search.label").count()).isZero();
    assertThat(registry.timer("search.lexical").count()).isZero();
    assertThat(registry.timer("search.embed_query").count()).isZero();
    assertThat(registry.timer("search.semantic").count()).isZero();
    assertThat(registry.timer("search.total").count()).isEqualTo(1);
    assertThat(events.list.getFirst().getFormattedMessage())
        .contains("label_hits=0", "lexical_hits=0", "semantic_hits=0", "client_hits=1");
  }

  @Test
  void derivesThePlanShapeFromMentionsAndResidual() {
    assertThat(planShapeOf(new QueryPlan("john", List.of(JOHN), "", Set.of(), Set.of())))
        .isEqualTo("identity");
    assertThat(planShapeOf(new QueryPlan("john bill", List.of(JOHN), "bill", Set.of(), Set.of())))
        .isEqualTo("compound");
    assertThat(planShapeOf(new QueryPlan("bill", List.of(), "bill", Set.of(), Set.of())))
        .isEqualTo("document");
  }

  @Test
  void stillRecordsAStageThatThrows() {
    Recording recording = telemetry.start();

    assertThatThrownBy(
            () ->
                recording.timed(
                    Stage.CLIENTS,
                    () -> {
                      throw new IllegalStateException("boom");
                    }))
        .isInstanceOf(IllegalStateException.class);
    recording.finish(1, 0);

    assertThat(registry.timer("search.clients").count()).isEqualTo(1);
    assertThat(events.list).hasSize(1);
  }

  private String planShapeOf(QueryPlan plan) {
    events.list.clear();
    Recording recording = telemetry.start();
    recording.planned(plan);
    recording.finish(1, 0);
    String message = events.list.getFirst().getFormattedMessage();
    return message.replaceAll(".*plan_shape=(\\w+).*", "$1");
  }
}
