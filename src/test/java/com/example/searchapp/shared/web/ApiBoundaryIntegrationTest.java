package com.example.searchapp.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.searchapp.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ApiBoundaryIntegrationTest extends IntegrationTest {
  @LocalServerPort private int port;

  @Test
  void publicPathsAreOpenAndProtectedPathsRequireTheApiKey() throws Exception {
    var health = get(port, "/health");
    assertThat(health.statusCode()).as(health.body()).isEqualTo(200);
    var apiDocs = get(port, "/v3/api-docs");
    assertThat(apiDocs.statusCode()).isEqualTo(200);
    assertThat(apiDocs.body()).contains("X-API-Key", "apiKey");
    assertThat(get(port, "/swagger-ui/index.html").statusCode()).isEqualTo(200);

    var missing = get(port, "/protected");
    assertThat(missing.statusCode()).isEqualTo(401);
    assertThat(missing.headers().firstValue("Content-Type").orElseThrow())
        .startsWith("application/problem+json");
    assertThat(missing.body()).contains("\"status\":401").doesNotContain("stackTrace");

    assertThat(get(port, "/protected", "wrong-key").statusCode()).isEqualTo(401);
    var unknownRoute = get(port, "/protected", TEST_API_KEY);
    assertThat(unknownRoute.statusCode()).isEqualTo(404);
    assertThat(unknownRoute.body())
        .contains("The requested resource was not found")
        .doesNotContain("static resource");
  }

  @Test
  void requestIdIsReturnedAndUncaughtExceptionsAreProblemDetails() throws Exception {
    var response = get(port, "/test/boom", TEST_API_KEY, "trace-123");

    assertThat(response.statusCode()).isEqualTo(500);
    assertThat(response.headers().firstValue("X-Request-Id").orElseThrow()).isEqualTo("trace-123");
    assertThat(response.headers().firstValue("Content-Type").orElseThrow())
        .startsWith("application/problem+json");
    assertThat(response.body())
        .contains("\"status\":500")
        .doesNotContain("secret database detail")
        .doesNotContain("stackTrace")
        .doesNotContain("constraint_name");
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ThrowingEndpointConfiguration {
    @Bean
    ThrowingEndpoint throwingEndpoint() {
      return new ThrowingEndpoint();
    }
  }

  @RestController
  static class ThrowingEndpoint {
    @GetMapping("/test/boom")
    String boom() {
      throw new IllegalStateException("secret database detail");
    }
  }
}
