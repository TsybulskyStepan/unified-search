package com.example.searchapp.search.planner;

import java.util.List;

/** The normalized query and the token metadata needed to resolve a mention once. */
public record NormalizedQuery(String text, List<NormalizedToken> tokens) {
  public NormalizedQuery {
    tokens = List.copyOf(tokens);
  }
}
