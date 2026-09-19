package com.example.searchapp.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
    byte[] body = request.getInputStream().readNBytes((int) MAX_BODY_BYTES + 1);
    if (body.length > MAX_BODY_BYTES) {
      ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.PAYLOAD_TOO_LARGE);
      problem.setTitle("Payload too large");
      problem.setDetail("The request body exceeds the 256 KB limit");
      problem.setInstance(java.net.URI.create(request.getRequestURI()));
      response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
      response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
      objectMapper.writeValue(response.getOutputStream(), problem);
      return;
    }
    filterChain.doFilter(new CachedBodyRequest(request, body), response);
  }

  private static final class CachedBodyRequest extends HttpServletRequestWrapper {
    private final byte[] body;

    private CachedBodyRequest(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body;
    }

    @Override
    public ServletInputStream getInputStream() {
      ByteArrayInputStream input = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        @Override
        public int read() {
          return input.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
          return input.read(bytes, offset, length);
        }

        @Override
        public boolean isFinished() {
          return input.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
          throw new UnsupportedOperationException();
        }
      };
    }

    @Override
    public BufferedReader getReader() throws IOException {
      Charset charset =
          getCharacterEncoding() == null
              ? StandardCharsets.UTF_8
              : Charset.forName(getCharacterEncoding());
      return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }
  }
}
