package com.example.searchapp.shared.taxonomy;

import java.util.List;

/**
 * One kind of document: its default purposes, the title and content patterns the classifier scores
 * against (§3.3), and the query synonyms the planner matches free text against.
 */
public record DocumentType(
    String id,
    String label,
    List<String> defaultPurposes,
    List<String> titlePatterns,
    List<String> contentPatterns,
    List<String> synonyms) {
  public DocumentType {
    defaultPurposes = List.copyOf(defaultPurposes);
    titlePatterns = List.copyOf(titlePatterns);
    contentPatterns = List.copyOf(contentPatterns);
    synonyms = List.copyOf(synonyms);
  }
}
