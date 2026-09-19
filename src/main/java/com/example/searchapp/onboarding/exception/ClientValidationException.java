package com.example.searchapp.onboarding.exception;

import java.util.Map;

public class ClientValidationException extends RuntimeException {
  private final Map<String, String> errors;

  public ClientValidationException(Map<String, String> errors) {
    this.errors = Map.copyOf(errors);
  }

  public Map<String, String> errors() {
    return errors;
  }
}
