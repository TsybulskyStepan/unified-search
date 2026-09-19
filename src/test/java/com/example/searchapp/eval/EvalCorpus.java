package com.example.searchapp.eval;

import java.util.List;

/**
 * The realistic KYC corpus loaded from {@code src/test/resources/eval/corpus.json} (§12.3). Also
 * the seed corpus (ticket 09), so the demo and the eval set can never drift apart.
 */
public record EvalCorpus(List<EvalClient> clients) {
  public record EvalClient(
      String firstName,
      String lastName,
      String email,
      String description,
      List<String> socialLinks,
      List<EvalDocument> documents) {}

  public record EvalDocument(String title, String content) {}
}
