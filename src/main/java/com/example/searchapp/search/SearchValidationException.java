package com.example.searchapp.search;

import java.util.Map;

class SearchValidationException extends RuntimeException {
  private final Map<String, String> errors;

  SearchValidationException(Map<String, String> errors) {
    this.errors = Map.copyOf(errors);
  }

  Map<String, String> errors() {
    return errors;
  }
}
