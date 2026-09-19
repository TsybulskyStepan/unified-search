package com.example.searchapp.shared.web;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Builds the {@link ProblemDetail} shape used by hand-constructed error responses across the app.
 */
public final class ProblemDetails {
  private ProblemDetails() {}

  public static ProblemDetail of(HttpStatus status, String title, String detail, URI instance) {
    ProblemDetail problem = ProblemDetail.forStatus(status);
    problem.setTitle(title);
    problem.setDetail(detail);
    problem.setInstance(instance);
    return problem;
  }
}
