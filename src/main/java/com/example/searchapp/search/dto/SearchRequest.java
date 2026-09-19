package com.example.searchapp.search.dto;

import com.example.searchapp.shared.web.RequestValidationException;
import java.util.LinkedHashMap;
import java.util.Map;

public record SearchRequest(String query, int limit, int offset) {
  private static final int DEFAULT_LIMIT = 20;

  public static SearchRequest of(String query, Integer limit, Integer offset) {
    String trimmedQuery = query == null ? null : query.trim();
    int resolvedLimit = limit == null ? DEFAULT_LIMIT : limit;
    int resolvedOffset = offset == null ? 0 : offset;
    Map<String, String> errors = new LinkedHashMap<>();
    if (trimmedQuery == null || trimmedQuery.isEmpty() || trimmedQuery.length() > 200) {
      errors.put("q", "must be between 1 and 200 characters");
    }
    if (resolvedLimit < 1 || resolvedLimit > 50) {
      errors.put("limit", "must be between 1 and 50");
    }
    if (resolvedOffset < 0) {
      errors.put("offset", "must be greater than or equal to 0");
    }
    if (!errors.isEmpty()) {
      throw new RequestValidationException(errors);
    }
    return new SearchRequest(trimmedQuery, resolvedLimit, resolvedOffset);
  }
}
