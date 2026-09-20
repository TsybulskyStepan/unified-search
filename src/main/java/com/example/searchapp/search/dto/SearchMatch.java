package com.example.searchapp.search.dto;

public sealed interface SearchMatch permits SearchMatch.Field, SearchMatch.Passage {
  public static SearchMatch field(String field, String tier) {
    return new Field(field, tier);
  }

  public static SearchMatch passage(
      String passage, java.util.List<String> signals, java.util.List<String> labels) {
    return new Passage(passage, signals, labels);
  }

  record Field(String field, String tier) implements SearchMatch {}

  record Passage(String passage, java.util.List<String> signals, java.util.List<String> labels)
      implements SearchMatch {}
}
