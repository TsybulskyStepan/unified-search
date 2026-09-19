package com.example.searchapp.onboarding.entity;

/** The closed set of {@code document.summary_status} values (§3.2). One spelling, not several. */
public final class SummaryStatus {
  public static final String NONE = "none";
  public static final String PENDING = "pending";
  public static final String READY = "ready";
  public static final String FAILED = "failed";

  private SummaryStatus() {}
}
