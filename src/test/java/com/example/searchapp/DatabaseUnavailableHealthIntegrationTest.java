package com.example.searchapp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.hikari.connection-timeout=1000",
      "app.api-key=" + IntegrationTest.TEST_API_KEY,
      "app.seed.enabled=false"
    })
@Testcontainers
class DatabaseUnavailableHealthIntegrationTest {
  // Not extending IntegrationTest: this test must stop its own container mid-test, and a shared
  // static container would be poisoned for every other test class sharing the same JVM fork.
  @Container @ServiceConnection
  static final PostgreSQLContainer<?> database =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

  @LocalServerPort private int port;

  @Test
  void healthReportsDownWhenTheDatabaseBecomesUnreachable() throws Exception {
    database.stop();

    var response = IntegrationTest.get(port, "/health");

    assertThat(response.statusCode()).isEqualTo(503);
    assertThat(response.body()).contains("\"status\":\"DOWN\"");
  }
}
