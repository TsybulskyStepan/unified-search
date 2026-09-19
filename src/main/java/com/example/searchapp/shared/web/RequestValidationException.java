package com.example.searchapp.shared.web;

import java.util.Map;

/**
 * A request failed validation that {@code jakarta.validation} bean annotations can't express —
 * cross-field or business-rule checks a DTO runs itself (e.g. a dotted email domain, an absolute
 * {@code http}/{@code https} URL). {@link GlobalExceptionHandler} maps this the same way it maps
 * {@link org.springframework.web.bind.MethodArgumentNotValidException}, so every validation failure
 * produces the same {@code ProblemDetail} shape regardless of which module or which validation
 * mechanism raised it (§1.3: both {@code onboarding} and {@code search} may use {@code shared}).
 */
public class RequestValidationException extends RuntimeException {
  private final Map<String, String> errors;

  public RequestValidationException(Map<String, String> errors) {
    this.errors = Map.copyOf(errors);
  }

  public Map<String, String> errors() {
    return errors;
  }
}
