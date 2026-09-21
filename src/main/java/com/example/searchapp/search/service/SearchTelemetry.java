package com.example.searchapp.search.service;

import com.example.searchapp.search.planner.QueryPlan;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Everything a search reports about itself: stage timers, and the one audit log line per request. A
 * {@link Recording} follows a single search; {@link Recording#finish} records the total and writes
 * the line.
 *
 * <p>The audit line is the only place a search logs, and it takes the query's length, never its
 * text, so query text cannot reach the logs from here.
 */
@Component
public class SearchTelemetry {
  private static final Logger log = LoggerFactory.getLogger(SearchTelemetry.class);

  public enum Stage {
    PLAN("search.plan"),
    CLIENTS("search.clients"),
    LABEL("search.label"),
    LEXICAL("search.lexical"),
    EMBED_QUERY("search.embed_query"),
    SEMANTIC("search.semantic");

    private final String metric;

    Stage(String metric) {
      this.metric = metric;
    }
  }

  private final Map<Stage, Timer> stageTimers = new EnumMap<>(Stage.class);
  private final Timer totalTimer;

  public SearchTelemetry(MeterRegistry meterRegistry) {
    for (Stage stage : Stage.values()) {
      stageTimers.put(stage, meterRegistry.timer(stage.metric));
    }
    totalTimer = meterRegistry.timer("search.total");
  }

  public Recording start() {
    return new Recording();
  }

  /** One search's measurements. Stages may be timed from other threads; the rest is not shared. */
  public final class Recording {
    private final long startNanos = System.nanoTime();
    private final AtomicLongArray stageNanos = new AtomicLongArray(Stage.values().length);
    private String planShape = "document";
    private int intentCount;
    private boolean mentionPresent;
    private int clientHits;
    private int labelHits;
    private int lexicalHits;
    private int semanticHits;

    private Recording() {}

    /** Runs the stage and records how long it took, including when it throws. */
    public <T> T timed(Stage stage, Supplier<T> operation) {
      long stageStartNanos = System.nanoTime();
      try {
        return operation.get();
      } finally {
        record(stage, System.nanoTime() - stageStartNanos);
      }
    }

    public void planned(QueryPlan plan) {
      mentionPresent = !plan.mentions().isEmpty();
      intentCount = plan.types().size() + plan.purposes().size();
      planShape = mentionPresent ? (plan.hasResidual() ? "compound" : "identity") : "document";
    }

    /** Only called when retrieval ran, so a skipped retrieval leaves its timers untouched. */
    public void retrieved(RetrievalMeasurements measured) {
      record(Stage.LABEL, measured.labelNanos());
      record(Stage.LEXICAL, measured.lexicalNanos());
      record(Stage.EMBED_QUERY, measured.queryEmbeddingNanos());
      record(Stage.SEMANTIC, measured.semanticNanos());
      labelHits = measured.labelHits();
      lexicalHits = measured.lexicalHits();
      semanticHits = measured.semanticHits();
    }

    public void clientHits(int hits) {
      clientHits = hits;
    }

    public void finish(int queryLength, int returned) {
      long totalNanos = System.nanoTime() - startNanos;
      totalTimer.record(totalNanos, TimeUnit.NANOSECONDS);
      log.info(
          "Search audit request_id={} query_length={} plan_shape={} intent_count={}"
              + " mention_present={} client_hits={} label_hits={} lexical_hits={} semantic_hits={}"
              + " returned={} plan_ms={} clients_ms={} label_ms={} lexical_ms={} embed_query_ms={}"
              + " semantic_ms={} total_ms={}",
          MDC.get("request_id"),
          queryLength,
          planShape,
          intentCount,
          mentionPresent,
          clientHits,
          labelHits,
          lexicalHits,
          semanticHits,
          returned,
          millis(Stage.PLAN),
          millis(Stage.CLIENTS),
          millis(Stage.LABEL),
          millis(Stage.LEXICAL),
          millis(Stage.EMBED_QUERY),
          millis(Stage.SEMANTIC),
          TimeUnit.NANOSECONDS.toMillis(totalNanos));
    }

    private void record(Stage stage, long nanos) {
      stageTimers.get(stage).record(nanos, TimeUnit.NANOSECONDS);
      stageNanos.set(stage.ordinal(), nanos);
    }

    private long millis(Stage stage) {
      return TimeUnit.NANOSECONDS.toMillis(stageNanos.get(stage.ordinal()));
    }
  }
}
