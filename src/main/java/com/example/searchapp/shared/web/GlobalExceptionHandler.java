package com.example.searchapp.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ProblemDetail handleTypeMismatch(
      MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
    if (exception.getRequiredType() == UUID.class) {
      return problem(
          HttpStatus.NOT_FOUND, "Not found", "The requested client was not found", request);
    }
    return problem(
        HttpStatus.BAD_REQUEST, "Invalid request", "The request could not be understood", request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail handleBeanValidation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    Map<String, String> errors = new LinkedHashMap<>();
    exception
        .getBindingResult()
        .getFieldErrors()
        .forEach(
            error ->
                errors.putIfAbsent(jsonFieldName(error.getField()), error.getDefaultMessage()));
    ProblemDetail problem =
        problem(
            HttpStatus.BAD_REQUEST, "Validation failed", "One or more fields are invalid", request);
    problem.setProperty("errors", errors);
    return problem;
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ProblemDetail handleUnreadableBody(
      HttpMessageNotReadableException exception, HttpServletRequest request) {
    return problem(
        HttpStatus.BAD_REQUEST, "Invalid request", "The request body could not be read", request);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ProblemDetail handleMissingResource(
      NoResourceFoundException exception, HttpServletRequest request) {
    return problem(
        HttpStatus.NOT_FOUND, "Not found", "The requested resource was not found", request);
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail handleUnexpectedException(Exception exception, HttpServletRequest request) {
    log.error("Unhandled exception class={}", exception.getClass().getName());
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Internal server error",
        "The request could not be completed",
        request);
  }

  private static ProblemDetail problem(
      HttpStatus status, String title, String detail, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatus(status);
    problem.setTitle(title);
    problem.setDetail(detail);
    problem.setInstance(java.net.URI.create(request.getRequestURI()));
    return problem;
  }

  private static String jsonFieldName(String field) {
    return field
        .replace("firstName", "first_name")
        .replace("lastName", "last_name")
        .replace("socialLinks", "social_links");
  }
}
