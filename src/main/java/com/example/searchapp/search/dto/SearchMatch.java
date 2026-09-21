package com.example.searchapp.search.dto;

import java.util.List;

/**
 * Why a result matched. A client result carries the {@link Field} that matched; a document result
 * carries the {@link Passage} and the signals and labels behind it. Jackson writes whichever record
 * it holds, so {@code match} is one of two JSON shapes, told apart by the result's {@code type}.
 */
public sealed interface SearchMatch permits SearchMatch.Field, SearchMatch.Passage {
  record Field(String field, String tier) implements SearchMatch {}

  record Passage(String passage, List<String> signals, List<String> labels)
      implements SearchMatch {}
}
