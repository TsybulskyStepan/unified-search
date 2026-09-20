package com.example.searchapp.search.planner;

import java.util.List;
import java.util.Set;

/**
 * One interpretation of a query, shared by every search-path stage. {@code mentions} holds the one
 * client the query names, or every client tied on the longest name match when the name is
 * ambiguous; it is empty when no client is named. {@code types} and {@code purposes} are the
 * taxonomy ids the residual text names; the taxonomy keeps the two id spaces disjoint (§3.2).
 */
public record QueryPlan(
    String query,
    List<ClientMention> mentions,
    String residual,
    Set<String> types,
    Set<String> purposes) {
  public QueryPlan {
    mentions = List.copyOf(mentions);
    types = Set.copyOf(types);
    purposes = Set.copyOf(purposes);
  }

  public boolean hasResidual() {
    return !residual.isEmpty();
  }
}
