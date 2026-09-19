package com.example.searchapp.onboarding.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.searchapp.SearchApplication;
import com.example.searchapp.search.dto.SearchRequest;
import com.example.searchapp.search.service.SearchService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
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
    boot(false).close();
  }

  @BeforeEach
  void emptyTheClientTable() throws Exception {
    try (Connection connection = jdbcConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE client CASCADE");
    }
  }

  @Test
  void seedsTheCorpusOnAnEmptyDatabaseAndSearchFindsIt() throws Exception {
    try (ConfigurableApplicationContext context = boot(true)) {
      assertThat(countClients()).isEqualTo(8);

      SearchService search = context.getBean(SearchService.class);
      SearchService.SearchPage page = search.search(SearchRequest.of("NevisWealth", null, null));

      assertThat(page.results()).isNotEmpty();
      assertThat(page.results().get(0).client().email()).isEqualTo("john.doe@neviswealth.com");
    }
  }

  @Test
  void doesNotSeedAgainOnASecondStartupOnceClientsExist() throws Exception {
    int corpusSize;
    try (ConfigurableApplicationContext first = boot(true)) {
      corpusSize = countClients();
      assertThat(corpusSize).isPositive();
    }

    // DemoSeeder runs again automatically during this second boot's startup; the table is no
    // longer empty, so it must skip rather than re-seed or fail on duplicate emails.
    try (ConfigurableApplicationContext ignored = boot(true)) {
      assertThat(countClients()).isEqualTo(corpusSize);
    }
  }

  @Test
  void seedingCanBeTurnedOffByConfiguration() throws Exception {
    try (ConfigurableApplicationContext context = boot(false)) {
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

    try (ConfigurableApplicationContext context = boot(true)) {
      // The startup seed already ran; empty the table again so both threads race to insert the
      // *same* client from a clean slate, as two instances starting at once would.
      try (Connection connection = jdbcConnection();
          Statement statement = connection.createStatement()) {
        statement.execute("TRUNCATE client CASCADE");
      }
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

  private static ConfigurableApplicationContext boot(boolean seedEnabled) {
    return new SpringApplicationBuilder(SearchApplication.class)
        .web(WebApplicationType.NONE)
        .run(
            "--app.api-key=" + TEST_API_KEY,
            "--app.seed.enabled=" + seedEnabled,
            "--spring.datasource.url=" + database.getJdbcUrl(),
            "--spring.datasource.username=" + database.getUsername(),
            "--spring.datasource.password=" + database.getPassword());
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
