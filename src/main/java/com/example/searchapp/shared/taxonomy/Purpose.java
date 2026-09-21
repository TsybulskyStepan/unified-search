package com.example.searchapp.shared.taxonomy;

import java.util.List;

/** One KYC question a document can answer, and the free-text queries that ask it. */
public record Purpose(String id, String label, List<String> synonyms) {
  public Purpose {
    synonyms = List.copyOf(synonyms);
  }
}
