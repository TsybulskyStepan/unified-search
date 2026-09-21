package com.example.searchapp.onboarding.service.model;

import java.util.List;

/**
 * The output of DocumentClassifier: what a document is, which KYC questions it answers, how that
 * was decided, and against which taxonomy version. Stamped onto the document row (§2.1) and used to
 * build the label chunk (§5.3). {@link #labelText()} is empty exactly when {@link #documentType()}
 * is {@link com.example.searchapp.shared.taxonomy.Taxonomy#UNKNOWN_TYPE}.
 */
public record Classification(
    String documentType,
    List<String> purposes,
    String source,
    int taxonomyVersion,
    String labelText) {

  /** A request named the type explicitly (§3.3 step 1). */
  public static final String SOURCE_REQUEST = "request";

  /** The rule scorer found a single best-scoring type (§3.3 step 2). */
  public static final String SOURCE_RULE = "rule";

  /** No type scored, or two or more tied for first place (§3.3 step 2). */
  public static final String SOURCE_UNKNOWN = "unknown";

  public Classification {
    purposes = List.copyOf(purposes);
  }
}
