package com.example.searchapp.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(NoResourceFoundException.class)
  ProblemDetail handleMissingResource(
      NoResourceFoundException exception, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
    problem.setTitle("Not found");
    problem.setDetail("The requested resource was not found");
    problem.setInstance(java.net.URI.create(request.getRequestURI()));
    return problem;
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail handleUnexpectedException(Exception exception, HttpServletRequest request) {
    log.error("Unhandled exception class={}", exception.getClass().getName());
    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    problem.setTitle("Internal server error");
    problem.setDetail("The request could not be completed");
    problem.setInstance(java.net.URI.create(request.getRequestURI()));
    return problem;
  }
}
