package com.example.searchapp.search.exception;

import java.util.Map;

public class SearchValidationException extends RuntimeException {
  private final Map<String, String> errors;

  public SearchValidationException(Map<String, String> errors) {
    this.errors = Map.copyOf(errors);
  }

  public Map<String, String> errors() {
    return errors;
  }
}
