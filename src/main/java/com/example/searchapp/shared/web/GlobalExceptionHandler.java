package com.example.searchapp.shared.web;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Extends {@link ResponseEntityExceptionHandler} rather than catching {@code Exception} directly. A
 * bare {@code @ExceptionHandler(Exception.class)} on a plain advice class would shadow Spring's own
 * handling of request-shape exceptions (validation failures, unreadable bodies, oversized uploads,
 * unsupported methods, missing resources), turning their correct 400/404/405/413 into a 500.
 * Extending the base class keeps those handlers in place; only exceptions Spring has no built-in
 * opinion about reach {@link #handleUnexpectedException}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(Exception.class)
  ProblemDetail handleUnexpectedException(Exception exception, WebRequest request) {
    log.error("Unhandled exception class={}", exception.getClass().getName(), exception);
    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    problem.setTitle("Internal server error");
    problem.setDetail("The request could not be completed");
    problem.setInstance(instanceUri(request));
    return problem;
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception exception,
      Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    if (body instanceof ProblemDetail problem && problem.getInstance() == null) {
      problem.setInstance(instanceUri(request));
    }
    return super.handleExceptionInternal(exception, body, headers, statusCode, request);
  }

  private static URI instanceUri(WebRequest request) {
    if (request instanceof ServletWebRequest servletWebRequest) {
      return URI.create(servletWebRequest.getRequest().getRequestURI());
    }
    return null;
  }
}
