package com.example.searchapp.shared.web;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Extends {@link ResponseEntityExceptionHandler} so Spring's request-shape exception mappings keep
 * their intended status codes. Only exceptions Spring has no built-in opinion about reach {@link
 * #handleUnexpectedException}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(Exception.class)
  ProblemDetail handleUnexpectedException(Exception exception, WebRequest request) {
    log.error("Unhandled exception class={}", exception.getClass().getName(), exception);
    return ProblemDetails.of(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Internal server error",
        "The request could not be completed",
        instanceUri(request));
  }

  @Override
  protected ResponseEntity<Object> handleTypeMismatch(
      TypeMismatchException exception,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    if (exception instanceof MethodArgumentTypeMismatchException argumentMismatch
        && argumentMismatch.getRequiredType() == UUID.class) {
      // §4.1: "A 404 is returned for a missing ID and for a malformed UUID." Deliberately generic
      // rather than naming an entity — this handler runs before any controller method, so it has
      // no reliable way to know which resource a malformed id was meant to identify (§4.1 has
      // several path variables named "id" alone), and guessing from the variable name would
      // couple shared/web to onboarding's path-naming convention (§1.3).
      ProblemDetail problem =
          ProblemDetails.of(
              HttpStatus.NOT_FOUND,
              "Not found",
              "The requested resource was not found",
              instanceUri(request));
      return handleExceptionInternal(exception, problem, headers, HttpStatus.NOT_FOUND, request);
    }
    return super.handleTypeMismatch(exception, headers, statusCode, request);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    Map<String, String> errors = new LinkedHashMap<>();
    exception
        .getBindingResult()
        .getFieldErrors()
        .forEach(
            error ->
                errors.putIfAbsent(jsonFieldName(error.getField()), error.getDefaultMessage()));
    ProblemDetail problem =
        ProblemDetails.of(
            HttpStatus.BAD_REQUEST,
            "Validation failed",
            "One or more fields are invalid",
            instanceUri(request));
    problem.setProperty("errors", errors);
    return handleExceptionInternal(exception, problem, headers, HttpStatus.BAD_REQUEST, request);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException exception,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    ProblemDetail problem =
        ProblemDetails.of(
            HttpStatus.BAD_REQUEST,
            "Invalid request",
            "The request body could not be read",
            instanceUri(request));
    return handleExceptionInternal(exception, problem, headers, HttpStatus.BAD_REQUEST, request);
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

  private static String jsonFieldName(String field) {
    return PropertyNamingStrategies.SnakeCaseStrategy.INSTANCE.translate(field);
  }
}
