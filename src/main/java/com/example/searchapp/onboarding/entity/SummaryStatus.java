package com.example.searchapp.onboarding.entity;

/**
 * The closed set of {@code document.summary_status} values (§3.2, §3.3): {@code text} + a database
 * {@code CHECK}, not a Java enum, so no JDBC cast is needed on either side. These constants exist
 * to give every SQL literal and comparison one spelling instead of several.
 */
public final class SummaryStatus {
  public static final String NONE = "none";
  public static final String PENDING = "pending";
  public static final String READY = "ready";
  public static final String FAILED = "failed";

  private SummaryStatus() {}
}
