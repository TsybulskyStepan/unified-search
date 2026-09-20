package com.example.searchapp.onboarding.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.searchapp.SearchApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Not extending {@code IntegrationTest}: these tests boot the real application repeatedly with
 * different {@code app.seed.enabled} values against a table forced empty between runs, which {@code
 * IntegrationTest}'s cached context and shared static container aren't built for — the same
 * reasoning {@code ApiKeyStartupFailureIntegrationTest} gives for owning its own container.
 */
@Testcontainers
class DemoSeederIntegrationTest {
  private static final String TEST_API_KEY = "test-api-key-that-is-at-least-32-characters";

  @Container
  static final PostgreSQLContainer<?> database =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

  @BeforeAll
  static void migrateSchemaOnce() {
    // A throwaway boot so Flyway creates the schema before the first @BeforeEach truncates it.
    boot(false, false).close();
  }

  @BeforeEach
  void emptyTheClientTable() throws Exception {
    truncateClientTable();
  }

  @Test
  void seedsTheCorpusOnAnEmptyDatabaseAndSearchFindsIt() throws Exception {
    try (ConfigurableApplicationContext context = boot(true, true)) {
      // Not a fixed count: the corpus grows independently of this test (§12.3), same reasoning as
      // doesNotSeedAgainOnASecondStartupOnceClientsExist below.
      assertThat(countClients()).isPositive();

      int port = Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
      HttpResponse<String> response = searchViaHttp(port, "NevisWealth");

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body())
          .contains("\"type\":\"client\"")
          .contains("\"email\":\"john.doe@neviswealth.com\"");
    }
  }

  @Test
  void doesNotSeedAgainOnASecondStartupOnceClientsExist() throws Exception {
    int corpusSize;
    try (ConfigurableApplicationContext first = boot(true, false)) {
      corpusSize = countClients();
      assertThat(corpusSize).isPositive();
    }

    // DemoSeeder runs again automatically during this second boot's startup; the table is no
    // longer empty, so it must skip rather than re-seed or fail on duplicate emails.
    try (ConfigurableApplicationContext ignored = boot(true, false)) {
      assertThat(countClients()).isEqualTo(corpusSize);
    }
  }

  @Test
  void seedingCanBeTurnedOffByConfiguration() throws Exception {
    try (ConfigurableApplicationContext context = boot(false, false)) {
      assertThatThrownBy(() -> context.getBean(DemoSeeder.class))
          .isInstanceOf(NoSuchBeanDefinitionException.class);
      assertThat(countClients()).isZero();
    }
  }

  @Test
  void twoConcurrentSeedersCannotDoubleSeedTheSameClient() throws Exception {
    var client =
        new DemoCorpus.DemoClient(
            "Race",
            "Condition",
            "race.condition@example.com",
            null,
            List.of(),
            List.of(new DemoCorpus.DemoDocument("Note", "A short note for the race test.")));

    try (ConfigurableApplicationContext context = boot(true, false)) {
      // The startup seed already ran; empty the table again so both threads race to insert the
      // *same* client from a clean slate, as two instances starting at once would.
      truncateClientTable();
      DemoSeeder seeder = context.getBean(DemoSeeder.class);

      ExecutorService pool = Executors.newFixedThreadPool(2);
      CountDownLatch bothReady = new CountDownLatch(2);
      CountDownLatch go = new CountDownLatch(1);
      try {
        List<Future<Boolean>> races =
            List.of(
                pool.submit(() -> race(seeder, client, bothReady, go)),
                pool.submit(() -> race(seeder, client, bothReady, go)));
        bothReady.await(10, TimeUnit.SECONDS);
        go.countDown();

        long succeeded = 0;
        for (Future<Boolean> race : races) {
          if (race.get(10, TimeUnit.SECONDS)) {
            succeeded++;
          }
        }
        assertThat(succeeded).isEqualTo(1);
      } finally {
        pool.shutdown();
      }
      assertThat(countClients()).isEqualTo(1);
    }
  }

  private static boolean race(
      DemoSeeder seeder, DemoCorpus.DemoClient client, CountDownLatch ready, CountDownLatch go)
      throws InterruptedException {
    ready.countDown();
    go.await(10, TimeUnit.SECONDS);
    return seeder.seedClient(client);
  }

  private static ConfigurableApplicationContext boot(boolean seedEnabled, boolean withWeb) {
    List<String> args =
        new ArrayList<>(
            List.of(
                "--app.api-key=" + TEST_API_KEY,
                "--app.seed.enabled=" + seedEnabled,
                "--spring.datasource.url=" + database.getJdbcUrl(),
                "--spring.datasource.username=" + database.getUsername(),
                "--spring.datasource.password=" + database.getPassword()));
    if (withWeb) {
      args.add("--server.port=0");
    }
    return new SpringApplicationBuilder(SearchApplication.class)
        .web(withWeb ? WebApplicationType.SERVLET : WebApplicationType.NONE)
        .run(args.toArray(String[]::new));
  }

  /** Matches {@code IntegrationTest.get}: the real endpoint, not the {@code SearchService} bean. */
  private static HttpResponse<String> searchViaHttp(int port, String query) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/search?q=" + query))
            .header("X-API-Key", TEST_API_KEY)
            .GET()
            .build();
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static void truncateClientTable() throws Exception {
    try (Connection connection = jdbcConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE client CASCADE");
    }
  }

  private static int countClients() throws Exception {
    try (Connection connection = jdbcConnection();
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("SELECT count(*) FROM client")) {
      resultSet.next();
      return resultSet.getInt(1);
    }
  }

  private static Connection jdbcConnection() throws Exception {
    return DriverManager.getConnection(
        database.getJdbcUrl(), database.getUsername(), database.getPassword());
  }
}
