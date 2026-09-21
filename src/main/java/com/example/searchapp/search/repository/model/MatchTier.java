package com.example.searchapp.search.repository.model;

/**
 * Whether a client match comes from an identity field (name, email, social link) or context
 * (description).
 */
public enum MatchTier {
  IDENTITY,
  CONTEXT;

  /** Lower-case name as stored in the database and used in SQL comparisons. */
  public String dbValue() {
    return name().toLowerCase();
  }

  /** Parse the lower-case value returned by the database. */
  public static MatchTier fromDb(String value) {
    return valueOf(value.toUpperCase());
  }
}
