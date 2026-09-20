package com.example.searchapp.search.planner;

import java.util.List;
import java.util.Set;

/**
 * One interpretation of a query, shared by every search-path stage. {@code mentions} holds the one
 * client the query names, or every client tied on the longest name match when the name is
 * ambiguous; it is empty when no client is named.
 */
public record QueryPlan(
    String query, List<ClientMention> mentions, String residual, Set<String> intents) {
  public QueryPlan {
    mentions = List.copyOf(mentions);
    intents = Set.copyOf(intents);
  }

  public boolean hasResidual() {
    return !residual.isEmpty();
  }
}
