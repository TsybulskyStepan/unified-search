package com.example.searchapp.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 50)
public class RequestBodySizeFilter extends OncePerRequestFilter {
  static final long MAX_BODY_BYTES = 256L * 1024L;
  private final ObjectMapper objectMapper;

  public RequestBodySizeFilter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (request.getContentLengthLong() > MAX_BODY_BYTES) {
      ProblemDetail problem =
          ProblemDetails.of(
              HttpStatus.PAYLOAD_TOO_LARGE,
              "Payload too large",
              "The request body exceeds the 256 KB limit",
              URI.create(request.getRequestURI()));
      response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
      response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
      objectMapper.writeValue(response.getOutputStream(), problem);
      return;
    }
    // This is a soft check: chunked requests without Content-Length bypass it. Add a hard byte
    // limit only if that becomes necessary.
    filterChain.doFilter(request, response);
  }
}
