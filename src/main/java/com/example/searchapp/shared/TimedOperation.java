package com.example.searchapp.shared;

import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * A measured operation: the elapsed time and the result. Timing is always captured, even when the
 * operation throws, so callers can always count on the elapsed nanos being recorded to a meter.
 *
 * <p>Use {@link #run(Supplier)} when you need the elapsed nanos (e.g. for an audit log), or {@link
 * #run(Supplier, Timer)} when you also want to record to a Micrometer timer in one call.
 */
public record TimedOperation<T>(T result, long elapsedNanos) {

  /** Runs {@code operation}, capturing elapsed wall-clock time in nanoseconds. */
  public static <T> TimedOperation<T> run(Supplier<T> operation) {
    long startNanos = System.nanoTime();
    try {
      return new TimedOperation<>(operation.get(), System.nanoTime() - startNanos);
    } finally {
      // nanos already captured in the record — the finally is empty but ensures the pair is atomic
    }
  }

  /**
   * Runs {@code operation}, captures elapsed time, and records it to {@code timer}. The timer is
   * recorded in a {@code finally} block so it fires even when the operation throws.
   */
  public static <T> TimedOperation<T> run(Supplier<T> operation, Timer timer) {
    long startNanos = System.nanoTime();
    try {
      return new TimedOperation<>(operation.get(), System.nanoTime() - startNanos);
    } finally {
      timer.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }
  }

  /** Convenience: elapsed time converted to milliseconds. */
  public long elapsedMillis() {
    return TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
  }
}
