package com.example.searchapp.search.dto;

public sealed interface SearchMatch permits SearchMatch.Field, SearchMatch.Passage {
  public static SearchMatch field(String field) {
    return new Field(field);
  }

  public static SearchMatch passage(String passage) {
    return new Passage(passage);
  }

  record Field(String field) implements SearchMatch {}

  record Passage(String passage) implements SearchMatch {}
}
