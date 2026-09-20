package com.example.searchapp.search.planner;

import java.util.Set;

/** One interpretation of a query, shared by every search-path stage. */
public record QueryPlan(String query, ClientMention mention, String residual, Set<String> intents) {
  public QueryPlan {
    intents = Set.copyOf(intents);
  }

  public boolean hasResidual() {
    return !residual.isEmpty();
  }
}
