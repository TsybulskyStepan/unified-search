package com.example.searchapp.onboarding.service;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * The test double ticket 10 builds the worker against — {@code SummaryWorker} never talks to a real
 * model until ticket 11's {@code GeminiSummarizer}. Each call consumes one queued response (a
 * summary or an exception to throw), falling back to a fixed default summary once the queue is
 * empty. {@link #callCount()} lets tests assert a no-op made no additional call. {@link
 * #pauseNextCall()} holds a call open until {@link #release()}, so a test can observe the document
 * genuinely {@code pending} — mid-call — instead of racing the worker's own (near-instant, async)
 * completion.
 */
public class StubSummarizer implements Summarizer {
  public static final String DEFAULT_SUMMARY = "stub summary";

  private final AtomicInteger callCount = new AtomicInteger();
  private final Queue<Function<Void, String>> responses = new ConcurrentLinkedQueue<>();
  private final AtomicReference<CountDownLatch> gate = new AtomicReference<>();

  @Override
  public String summarize(String title, String content) {
    callCount.incrementAndGet();
    CountDownLatch latch = gate.get();
    if (latch != null) {
      await(latch);
    }
    Function<Void, String> response = responses.poll();
    return response == null ? DEFAULT_SUMMARY : response.apply(null);
  }

  /** The next call (and every call until {@link #release()}) blocks before returning. */
  public void pauseNextCall() {
    gate.set(new CountDownLatch(1));
  }

  /** Releases any call currently blocked in {@link #pauseNextCall()}. */
  public void release() {
    CountDownLatch latch = gate.get();
    if (latch != null) {
      latch.countDown();
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(exception);
    }
  }

  /** Makes the next call throw {@code exception} instead of returning a summary. */
  public void queueFailure(RuntimeException exception) {
    responses.add(
        ignored -> {
          throw exception;
        });
  }

  public int callCount() {
    return callCount.get();
  }

  /** Resets call count, queued responses and any pause; each test starts from a clean double. */
  public void reset() {
    callCount.set(0);
    responses.clear();
    gate.set(null);
  }
}
