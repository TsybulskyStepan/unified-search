package com.example.searchapp.onboarding.exception;

import com.example.searchapp.shared.web.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps every exception onboarding's controllers raise — beyond validation, which {@code
 * shared.web.GlobalExceptionHandler} already covers for every module (§1.3) — to {@link
 * ProblemDetail} (§8.2). Scoped to {@code onboarding} rather than living in {@code shared.web}:
 * these exception types belong to this module, and {@code shared} must not depend on it.
 *
 * <p>All of it lives in one place now, not split between here and a controller-local handler: "not
 * found" was already shared across {@code ClientController} and {@code DocumentController}; {@link
 * DuplicateClientEmailException} is raised by only one controller, but there is no longer a
 * validation-style exception left to justify keeping anything local.
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

  @ExceptionHandler(DuplicateClientEmailException.class)
  ProblemDetail handleDuplicateEmail(
      DuplicateClientEmailException exception, HttpServletRequest request) {
    return ProblemDetails.of(
        HttpStatus.CONFLICT,
        "Conflict",
        "A client with this email already exists",
        URI.create(request.getRequestURI()));
  }
}
