package com.example.searchapp;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "app.api-key=" + IntegrationTest.TEST_API_KEY,
      // Tests create their own clients (some reusing seed-corpus emails, e.g. NevisWealth); demo
      // seeding would collide with them. DemoSeederIntegrationTest exercises seeding directly.
      "app.seed.enabled=false"
    })
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class IntegrationTest {
  protected static final String TEST_API_KEY = "test-api-key-that-is-at-least-32-characters";

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> database =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

  protected static HttpResponse<String> get(int port, String path)
      throws IOException, InterruptedException {
    return get(port, path, null, null);
  }

  protected static HttpResponse<String> get(int port, String path, String apiKey)
      throws IOException, InterruptedException {
    return get(port, path, apiKey, null);
  }

  protected static HttpResponse<String> get(int port, String path, String apiKey, String trace)
      throws IOException, InterruptedException {
    var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
    if (apiKey != null) {
      builder.header("X-API-Key", apiKey);
    }
    if (trace != null) {
      builder.header("X-Cloud-Trace-Context", trace);
    }
    return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  protected static HttpResponse<String> post(int port, String path, String apiKey, String body)
      throws IOException, InterruptedException {
    var builder =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (apiKey != null) {
      builder.header("X-API-Key", apiKey);
    }
    return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }
}
