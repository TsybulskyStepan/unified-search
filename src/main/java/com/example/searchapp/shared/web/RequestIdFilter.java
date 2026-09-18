package com.example.searchapp.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);
  static final String REQUEST_ID = "request_id";
  private static final String TRACE_HEADER = "X-Cloud-Trace-Context";
  private static final String RESPONSE_HEADER = "X-Request-Id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = requestId(request.getHeader(TRACE_HEADER));
    MDC.put(REQUEST_ID, requestId);
    response.setHeader(RESPONSE_HEADER, requestId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      log.info("request completed method={} status={}", request.getMethod(), response.getStatus());
      MDC.remove(REQUEST_ID);
    }
  }

  private static String requestId(String traceHeader) {
    if (traceHeader != null
        && traceHeader.length() <= 128
        && traceHeader.matches("[A-Za-z0-9._;=/-]+")) {
      return traceHeader;
    }
    return UUID.randomUUID().toString();
  }
}
