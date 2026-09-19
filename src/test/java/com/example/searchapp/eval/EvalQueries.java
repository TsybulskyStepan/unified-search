package com.example.searchapp.eval;

import java.util.List;

/**
 * The query set loaded from {@code src/test/resources/eval/queries.json} (§12.3): category queries
 * that must embed close to a specific document despite sharing no words with it, and out-of-domain
 * queries that must not.
 */
public record EvalQueries(List<PositivePair> positives, List<String> negatives) {
  public record PositivePair(String query, String clientEmail, String documentTitle) {}
}
