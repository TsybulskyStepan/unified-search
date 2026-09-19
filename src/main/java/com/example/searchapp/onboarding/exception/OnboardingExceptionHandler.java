package com.example.searchapp.onboarding.exception;

import com.example.searchapp.shared.web.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the "not found" exceptions shared across onboarding's controllers to {@link ProblemDetail}
 * (§8.2). Scoped to {@code onboarding} rather than living in {@code shared.web.
 * GlobalExceptionHandler}: these exception types belong to this module, and {@code shared} must not
 * depend on it (§1.3 module boundary).
 *
 * <p>Validation and conflict exceptions ({@link ClientValidationException}, {@link
 * DuplicateClientEmailException}) stay local to {@code ClientController}, the only place that
 * raises them. "Not found" is raised by more than one controller — {@code ClientController} and
 * {@code DocumentController} both look up a client — which is why it is centralized here instead.
 */
@RestControllerAdvice(basePackages = "com.example.searchapp.onboarding")
public class OnboardingExceptionHandler {

  @ExceptionHandler(ClientNotFoundException.class)
  ProblemDetail handleClientNotFound(
      ClientNotFoundException exception, HttpServletRequest request) {
    return ProblemDetails.of(
        HttpStatus.NOT_FOUND,
        "Not found",
        "The requested client was not found",
        URI.create(request.getRequestURI()));
  }

  @ExceptionHandler(DocumentNotFoundException.class)
  ProblemDetail handleDocumentNotFound(
      DocumentNotFoundException exception, HttpServletRequest request) {
    return ProblemDetails.of(
        HttpStatus.NOT_FOUND,
        "Not found",
        "The requested document was not found",
        URI.create(request.getRequestURI()));
  }
}
