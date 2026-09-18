package com.example.searchapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ApiBoundaryIntegrationTest extends IntegrationTest {
  private static final String API_KEY = "test-api-key-that-is-at-least-32-characters";

  @LocalServerPort private int port;

  @Test
  void publicPathsAreOpenAndProtectedPathsRequireTheApiKey() throws Exception {
    var health = get("/health");
    assertThat(health.statusCode()).as(health.body()).isEqualTo(200);
    var apiDocs = get("/v3/api-docs");
    assertThat(apiDocs.statusCode()).isEqualTo(200);
    assertThat(apiDocs.body()).contains("X-API-Key", "apiKey");
    assertThat(get("/swagger-ui/index.html").statusCode()).isEqualTo(200);

    var missing = get("/protected");
    assertThat(missing.statusCode()).isEqualTo(401);
    assertThat(missing.headers().firstValue("Content-Type").orElseThrow())
        .startsWith("application/problem+json");
    assertThat(missing.body()).contains("\"status\":401").doesNotContain("stackTrace");

    assertThat(get("/protected", "wrong-key").statusCode()).isEqualTo(401);
    assertThat(get("/protected", API_KEY).statusCode()).isEqualTo(404);
  }

  @Test
  void requestIdIsReturnedAndUncaughtExceptionsAreProblemDetails() throws Exception {
    var response = get("/test/boom", API_KEY, "trace-123");

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

  private HttpResponse<String> get(String path) throws Exception {
    return get(path, null, null);
  }

  private HttpResponse<String> get(String path, String apiKey) throws Exception {
    return get(path, apiKey, null);
  }

  private HttpResponse<String> get(String path, String apiKey, String trace) throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
    if (apiKey != null) {
      builder.header("X-API-Key", apiKey);
    }
    if (trace != null) {
      builder.header("X-Cloud-Trace-Context", trace);
    }
    return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
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
