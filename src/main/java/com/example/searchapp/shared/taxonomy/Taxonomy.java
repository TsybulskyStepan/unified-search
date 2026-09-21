package com.example.searchapp.shared.taxonomy;

import java.util.Map;

/**
 * The closed vocabulary of document types and KYC purposes (system-design §3.1): what a document
 * is, and which questions it answers. Loaded once at startup by {@link TaxonomyLoader} and shared,
 * unchanged, between {@code onboarding} and {@code search} — the two modules never import each
 * other (§1.3), so this file, reached by both through {@code shared}, is the only thing keeping the
 * labels one module writes and the intents the other reads meaning the same thing.
 *
 * <p>{@link #version()} is stamped on every document a classifier produces from this taxonomy, so a
 * later change to the file can be detected against rows written under an older one.
 */
public record Taxonomy(
    int version, Map<String, DocumentType> types, Map<String, Purpose> purposes) {

  /**
   * The one document type not named in the file. A document the classifier cannot place against any
   * pattern gets this type and no purposes, rather than a guess (§3.3). Reserved: the file may not
   * define a type or purpose with this id.
   */
  public static final String UNKNOWN_TYPE = "unknown";

  public Taxonomy {
    types = Map.copyOf(types);
    purposes = Map.copyOf(purposes);
  }
}
