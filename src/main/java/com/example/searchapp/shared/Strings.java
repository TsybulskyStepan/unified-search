package com.example.searchapp.shared;

import java.util.List;

/** Shared string utilities used across modules. */
public final class Strings {

  private Strings() {}

  /** {@link String#trim()} that returns {@code null} for null input. */
  public static String trim(String value) {
    return value == null ? null : value.trim();
  }

  /** {@link #trim} that returns {@code null} for blank or empty results. */
  public static String trimToNull(String value) {
    String trimmed = trim(value);
    return trimmed == null || trimmed.isEmpty() ? null : trimmed;
  }

  /** Trims every element in a list, returning an empty list for null input. */
  public static List<String> trimAll(List<String> values) {
    if (values == null) {
      return List.of();
    }
    return values.stream().map(Strings::trim).toList();
  }
}
