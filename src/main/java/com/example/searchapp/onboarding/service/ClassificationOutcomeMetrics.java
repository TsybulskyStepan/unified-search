package com.example.searchapp.onboarding.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ClassificationOutcomeMetrics {
  private final MeterRegistry meterRegistry;

  public ClassificationOutcomeMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void record(Classification classification) {
    meterRegistry
        .counter(
            "classification.outcome",
            "type",
            classification.documentType(),
            "source",
            classification.source())
        .increment();
  }
}
