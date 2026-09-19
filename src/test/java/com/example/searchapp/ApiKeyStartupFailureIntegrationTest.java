package com.example.searchapp;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * {@link ApiKeyPropertiesTest} exercises {@link com.example.searchapp.shared.web.ApiKeyProperties}
 * directly, which never reaches Spring's own {@code @ConfigurationProperties} binding failure path
 * — the one an actually-misconfigured deployment hits. This test starts the real application with a
 * too-short key and asserts context startup fails for that reason.
 *
 * <p>Not extending {@link IntegrationTest}: this builds an extra, unmanaged {@link
 * org.springframework.context.ConfigurableApplicationContext} by hand outside Spring's test context
 * caching, and doing that against the shared static container broke a sibling test class ({@code
 * HealthAndSchemaIntegrationTest} started failing with a stale, already-closed JDBC port) — the
 * same class of interference {@code DatabaseUnavailableHealthIntegrationTest} avoids by owning its
 * own container. This class does too, for the same reason.
 */
@Testcontainers
class ApiKeyStartupFailureIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> database =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

  @Test
  void contextFailsToStartWithATooShortApiKey() {
    assertThatThrownBy(
            () ->
                new SpringApplicationBuilder(SearchApplication.class)
                    .web(WebApplicationType.NONE)
                    .run(
                        "--app.api-key=too-short",
                        "--spring.datasource.url=" + database.getJdbcUrl(),
                        "--spring.datasource.username=" + database.getUsername(),
                        "--spring.datasource.password=" + database.getPassword()))
        .rootCause()
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("32 characters");
  }
}
