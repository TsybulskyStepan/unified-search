package com.example.searchapp.onboarding.seed;

import java.util.List;

/**
 * The realistic KYC corpus loaded from {@code seed/corpus.json} (§12.3). This is also the eval
 * set's corpus — {@code src/test/resources/eval/} has no copy of its own; the file lives once,
 * under {@code src/main/resources/seed/}, on the classpath both {@link DemoSeeder} and the eval
 * tests read from — so the demo and the eval set can never drift apart (§11.3).
 */
public record DemoCorpus(List<DemoClient> clients) {
  public record DemoClient(
      String firstName,
      String lastName,
      String email,
      String description,
      List<String> socialLinks,
      List<DemoDocument> documents) {}

  public record DemoDocument(String title, String content) {}
}
