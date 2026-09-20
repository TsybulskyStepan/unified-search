package com.example.searchapp.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rewrites incoming {@code /api/*} paths to their internal equivalents so the React SPA can call
 * the existing API endpoints through a consistent {@code /api} prefix (§13, follow-up 7). Runs
 * before {@link ApiKeyFilter} so auth decisions apply to the rewritten path.
 *
 * <p>For example: {@code GET /api/search?q=hello} → {@code GET /search?q=hello}.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 200)
public class ApiRewriteFilter extends OncePerRequestFilter {

  private static final String API_PREFIX = "/api";

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith(API_PREFIX);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    String rewritten = path.substring(API_PREFIX.length());
    if (rewritten.isEmpty()) {
      rewritten = "/";
    }
    filterChain.doFilter(new RewrittenPathRequest(request, rewritten), response);
  }

  private static class RewrittenPathRequest extends HttpServletRequestWrapper {

    private final String rewrittenPath;

    RewrittenPathRequest(HttpServletRequest request, String rewrittenPath) {
      super(request);
      this.rewrittenPath = rewrittenPath;
    }

    @Override
    public String getRequestURI() {
      return rewrittenPath;
    }

    @Override
    public StringBuffer getRequestURL() {
      StringBuffer url = ((HttpServletRequest) getRequest()).getRequestURL();
      int apiIndex = url.indexOf("/api");
      if (apiIndex >= 0) {
        return new StringBuffer(url.substring(0, apiIndex)).append(rewrittenPath);
      }
      return url;
    }

    @Override
    public String getServletPath() {
      return rewrittenPath;
    }
  }
}
