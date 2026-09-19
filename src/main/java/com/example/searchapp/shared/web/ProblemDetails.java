package com.example.searchapp.shared.web;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Builds the {@link ProblemDetail} shape this app returns for its own hand-constructed error
 * responses — {@link ApiKeyFilter}'s 401, which runs before the {@code DispatcherServlet} and so
 * cannot go through {@link GlobalExceptionHandler}, and that class's own fallback for exceptions
 * Spring has no built-in opinion about.
 */
final class ProblemDetails {
  private ProblemDetails() {}

  static ProblemDetail of(HttpStatus status, String title, String detail, URI instance) {
    ProblemDetail problem = ProblemDetail.forStatus(status);
    problem.setTitle(title);
    problem.setDetail(detail);
    problem.setInstance(instance);
    return problem;
  }
}
