package com.example.searchapp.onboarding.service;

import com.example.searchapp.onboarding.exception.PermanentSummarizationException;
import com.example.searchapp.onboarding.repository.ClaimedSummaryJob;
import com.example.searchapp.onboarding.repository.DocumentRepository;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The DB-backed summary queue (§7.1, §7.2). {@code document} is the queue: a row claimed under a
 * lease, summarized outside any transaction, then completed with a guarded update. {@link
 * #runOnce()} is the whole cycle — claim a batch, process each, sweep attempts-exhausted rows to
 * {@code failed} — and it is safe to call concurrently or repeatedly: the claim SQL's {@code FOR
 * UPDATE SKIP LOCKED} is what makes that safe, not anything in this class.
 *
 * <p>Two triggers share {@link #runOnce()}: {@link #nudge()}, fired by the request handler for the
 * fast path, off the request thread so {@code POST …/summary} returns before any model call; and
 * {@link #sweep()}, a fixed-delay tick that recovers a nudge lost to a restart and doubles as retry
 * backoff — a row's lease keeps it unclaimable until the sweep interval has had a chance to pass.
 */
@Component
public class SummaryWorker {
  private static final Logger log = LoggerFactory.getLogger(SummaryWorker.class);
  private static final int CLAIM_BATCH = 5;

  private final DocumentRepository documents;
  private final Summarizer summarizer;
  private final ExecutorService nudgeExecutor = Executors.newVirtualThreadPerTaskExecutor();

  public SummaryWorker(DocumentRepository documents, Summarizer summarizer) {
    this.documents = documents;
    this.summarizer = summarizer;
  }

  /** Fire-and-forget: returns immediately, the claim-and-process cycle runs on its own thread. */
  public void nudge() {
    nudgeExecutor.execute(this::runOnce);
  }

  @Scheduled(fixedDelay = 30_000)
  public void sweep() {
    runOnce();
  }

  public void runOnce() {
    int exhausted = documents.markExhaustedAsFailed();
    if (exhausted > 0) {
      log.info("Summary sweep outcome=failed count={}", exhausted);
    }
    List<ClaimedSummaryJob> claimed = documents.claimPending(CLAIM_BATCH);
    for (ClaimedSummaryJob job : claimed) {
      process(job);
    }
  }

  private void process(ClaimedSummaryJob job) {
    long startNanos = System.nanoTime();
    try {
      String summary = summarizer.summarize(job.title(), job.content());
      documents.completeSummarySuccess(job.id(), summary);
      log.info(
          "Summary outcome document_id={} attempt={} outcome=ready latency_ms={}",
          job.id(),
          job.attempts(),
          latencyMillis(startNanos));
    } catch (PermanentSummarizationException exception) {
      documents.completeSummaryFailed(job.id());
      log.warn(
          "Summary outcome document_id={} attempt={} outcome=failed error={} latency_ms={}",
          job.id(),
          job.attempts(),
          exception.getClass().getSimpleName(),
          latencyMillis(startNanos));
    } catch (RuntimeException exception) {
      // TransientSummarizationException, and any other unexpected failure treated the same way:
      // the attempt is already consumed at claim time, the lease guards against an immediate
      // re-claim, and the row is left pending — exhausted by markExhaustedAsFailed() once its
      // attempts run out and its lease expires (§7.2). A poison document cannot loop forever.
      log.warn(
          "Summary outcome document_id={} attempt={} outcome=retry error={} latency_ms={}",
          job.id(),
          job.attempts(),
          exception.getClass().getSimpleName(),
          latencyMillis(startNanos));
    }
  }

  private static long latencyMillis(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000;
  }

  @PreDestroy
  void closeNudgeExecutor() {
    nudgeExecutor.close();
  }
}
