package com.example.searchapp.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Component
// Runs after RequestIdFilter (HIGHEST_PRECEDENCE), so X-Request-Id is set on every response
// including a 401. The -100 offset, rather than exactly LOWEST_PRECEDENCE, leaves headroom for a
// filter that must run after auth but still ahead of the servlet dispatch.
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class ApiKeyFilter extends OncePerRequestFilter {
  private static final String API_KEY_HEADER = "X-API-Key";
  private final byte[] expectedKey;
  private final ObjectMapper objectMapper;

  public ApiKeyFilter(ApiKeyProperties properties, ObjectMapper objectMapper) {
    this.expectedKey = properties.apiKey().getBytes(StandardCharsets.UTF_8);
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    boolean isGet = "GET".equals(request.getMethod());
    return (isGet && "/health".equals(path))
        || (isGet && "/".equals(path))
        || path.equals("/v3/api-docs")
        || path.startsWith("/v3/api-docs/")
        || path.equals("/swagger-ui.html")
        || path.startsWith("/swagger-ui/")
        || path.startsWith("/assets/")
        || path.equals("/favicon.ico");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String suppliedKey = request.getHeader(API_KEY_HEADER);
    byte[] suppliedBytes =
        suppliedKey == null ? new byte[0] : suppliedKey.getBytes(StandardCharsets.UTF_8);
    if (suppliedKey == null
        || suppliedKey.isBlank()
        || !MessageDigest.isEqual(expectedKey, suppliedBytes)) {
      ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
      problem.setTitle("Unauthorized");
      problem.setDetail("A valid API key is required");
      response.setStatus(HttpStatus.UNAUTHORIZED.value());
      response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
      objectMapper.writeValue(response.getOutputStream(), problem);
      return;
    }
    filterChain.doFilter(request, response);
  }
}
