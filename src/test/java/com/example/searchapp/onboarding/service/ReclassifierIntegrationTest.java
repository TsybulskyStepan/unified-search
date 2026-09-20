package com.example.searchapp.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.searchapp.SearchApplication;
import com.example.searchapp.onboarding.seed.DemoSeeder;
import com.example.searchapp.shared.taxonomy.Taxonomy;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * {@link Reclassifier} at real startup (§3.4): its own database and application boots, not {@code
 * IntegrationTest}'s shared ones, because every test here needs to insert stale rows before the
 * startup run it is testing — the same reasoning {@code DemoSeederIntegrationTest} gives for the
 * same pattern.
 */
@Testcontainers
class ReclassifierIntegrationTest {
  private static final String TEST_API_KEY = "test-api-key-that-is-at-least-32-characters";

  @Container
  static final PostgreSQLContainer<?> database =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

  @BeforeAll
  static void migrateSchemaOnce() {
    boot(false).close();
  }

  @AfterAll
  static void emptyTheDocumentTable() throws Exception {
    truncateDocumentAndClientTables();
  }

  @Test
  void reclassifiesARuleSourcedRowAndRewritesItsLabelChunk() throws Exception {
    truncateDocumentAndClientTables();
    UUID clientId = insertClient("rule.stale@example.com");
    UUID documentId =
        insertStaleDocument(
            clientId,
            "2024 Utility Bill",
            "Account 123, due 15 June.",
            "unknown",
            List.of(),
            "unknown");

    try (ConfigurableApplicationContext context = boot(false)) {
      assertThat(taxonomyVersionOf(documentId)).isEqualTo(currentTaxonomyVersion(context));
      assertThat(documentTypeOf(documentId)).isEqualTo("utility_bill");
      assertThat(purposesOf(documentId)).containsExactly("proof_of_address");
      assertThat(classificationSourceOf(documentId)).isEqualTo("rule");
      assertThat(labelChunkCount(documentId)).isEqualTo(1);
      assertThat(
              context
                  .getBean(MeterRegistry.class)
                  .find("classification.outcome")
                  .tags("type", "utility_bill", "source", "rule")
                  .counter()
                  .count())
          .isEqualTo(1);
    }
  }

  @Test
  void keepsARequestSourcedRowsTypeWhileRefreshingItsLabelAndVersion() throws Exception {
    truncateDocumentAndClientTables();
    UUID clientId = insertClient("request.stale@example.com");
    // Content that would rule-classify completely differently, proving the stored type is kept
    // rather than recomputed.
    UUID documentId =
        insertStaleDocument(
            clientId,
            "Some Notes",
            "Passport number and nationality mentioned here.",
            "bank_statement",
            List.of("source_of_funds"),
            "request");

    try (ConfigurableApplicationContext ignored = boot(false)) {
      assertThat(documentTypeOf(documentId)).isEqualTo("bank_statement");
      assertThat(purposesOf(documentId)).containsExactly("source_of_funds");
      assertThat(classificationSourceOf(documentId)).isEqualTo("request");
      assertThat(taxonomyVersionOf(documentId)).isEqualTo(currentTaxonomyVersion(ignored));
    }
  }

  @Test
  void aRowAtTheCurrentVersionIsLeftUntouched() throws Exception {
    truncateDocumentAndClientTables();
    UUID clientId = insertClient("current.version@example.com");
    UUID documentId;
    try (ConfigurableApplicationContext context = boot(false)) {
      documentId =
          insertDocumentAtVersion(
              clientId,
              "Trust Deed Amendment",
              "Trustee and settlor details.",
              "trust_deed",
              List.of("trust_structure"),
              "rule",
              currentTaxonomyVersion(context));
    }

    try (ConfigurableApplicationContext ignored = boot(false)) {
      assertThat(documentTypeOf(documentId)).isEqualTo("trust_deed");
      assertThat(classificationSourceOf(documentId)).isEqualTo("rule");
    }
  }

  @Test
  void drainsMoreThanOneBatchOfStaleRows() throws Exception {
    truncateDocumentAndClientTables();
    UUID clientId = insertClient("many.stale@example.com");
    List<UUID> documentIds = new ArrayList<>();
    for (int i = 0; i < 101; i++) {
      documentIds.add(
          insertStaleDocument(
              clientId,
              "2024 Utility Bill " + i,
              "Account " + i + ".",
              "unknown",
              List.of(),
              "unknown"));
    }

    try (ConfigurableApplicationContext ignored = boot(false)) {
      for (UUID documentId : documentIds) {
        assertThat(documentTypeOf(documentId)).isEqualTo("utility_bill");
      }
    }
  }

  @Test
  void runsBeforeTheSeederSoASeededDocumentIsNeverStale() throws Exception {
    truncateDocumentAndClientTables();
    ListAppender<ILoggingEvent> events = new ListAppender<>();
    events.start();
    // Spring Boot resets the Logback context while preparing each fresh application, which would
    // detach an appender attached before boot(). ApplicationStartedEvent fires after that reset
    // (logging is already configured) but before ApplicationRunners execute, so attaching here is
    // the one moment guaranteed to both survive and capture the ordering under test.
    ApplicationListener<ApplicationStartedEvent> attach =
        event -> {
          ((Logger) LoggerFactory.getLogger(Reclassifier.class)).addAppender(events);
          ((Logger) LoggerFactory.getLogger(DemoSeeder.class)).addAppender(events);
        };

    try (ConfigurableApplicationContext ignored =
        new SpringApplicationBuilder(SearchApplication.class)
            .web(WebApplicationType.NONE)
            .listeners(attach)
            .run(
                "--app.api-key=" + TEST_API_KEY,
                "--app.seed.enabled=true",
                "--spring.datasource.url=" + database.getJdbcUrl(),
                "--spring.datasource.username=" + database.getUsername(),
                "--spring.datasource.password=" + database.getPassword())) {
      // startup already ran both runners by the time run() returns
    }

    List<String> loggerNames = events.list.stream().map(ILoggingEvent::getLoggerName).toList();
    int reclassifierIndex = loggerNames.indexOf(Reclassifier.class.getName());
    int seederIndex = loggerNames.indexOf(DemoSeeder.class.getName());
    assertThat(reclassifierIndex)
        .as("Reclassifier must log before DemoSeeder: %s", loggerNames)
        .isGreaterThanOrEqualTo(0)
        .isLessThan(seederIndex);
  }

  private static int currentTaxonomyVersion(ConfigurableApplicationContext context) {
    return context.getBean(Taxonomy.class).version();
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

  private static UUID insertClient(String email) throws Exception {
    UUID id = UUID.randomUUID();
    try (Connection connection = jdbcConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                "INSERT INTO client (id, first_name, last_name, email) VALUES (?, ?, ?, ?)")) {
      statement.setObject(1, id);
      statement.setString(2, "Stale");
      statement.setString(3, "Row");
      statement.setString(4, email);
      statement.executeUpdate();
    }
    return id;
  }

  private static UUID insertStaleDocument(
      UUID clientId,
      String title,
      String content,
      String documentType,
      List<String> purposes,
      String classificationSource)
      throws Exception {
    return insertDocumentAtVersion(
        clientId, title, content, documentType, purposes, classificationSource, 0);
  }

  private static UUID insertDocumentAtVersion(
      UUID clientId,
      String title,
      String content,
      String documentType,
      List<String> purposes,
      String classificationSource,
      int taxonomyVersion)
      throws Exception {
    UUID id = UUID.randomUUID();
    try (Connection connection = jdbcConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                """
                INSERT INTO document
                    (id, client_id, title, content, summary_status,
                     document_type, purposes, classification_source, taxonomy_version, label_text)
                VALUES (?, ?, ?, ?, 'none', ?, ?, ?, ?, '')
                """)) {
      Array purposesArray = connection.createArrayOf("text", purposes.toArray());
      statement.setObject(1, id);
      statement.setObject(2, clientId);
      statement.setString(3, title);
      statement.setString(4, content);
      statement.setString(5, documentType);
      statement.setArray(6, purposesArray);
      statement.setString(7, classificationSource);
      statement.setInt(8, taxonomyVersion);
      statement.executeUpdate();
    }
    return id;
  }

  private static int taxonomyVersionOf(UUID documentId) throws Exception {
    return scalar(
        "SELECT taxonomy_version FROM document WHERE id = ?", documentId, ResultSet::getInt);
  }

  private static String documentTypeOf(UUID documentId) throws Exception {
    return scalar(
        "SELECT document_type FROM document WHERE id = ?", documentId, ResultSet::getString);
  }

  private static String classificationSourceOf(UUID documentId) throws Exception {
    return scalar(
        "SELECT classification_source FROM document WHERE id = ?",
        documentId,
        ResultSet::getString);
  }

  private static List<String> purposesOf(UUID documentId) throws Exception {
    try (Connection connection = jdbcConnection();
        PreparedStatement statement =
            connection.prepareStatement("SELECT purposes FROM document WHERE id = ?")) {
      statement.setObject(1, documentId);
      try (ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        String[] purposes = (String[]) resultSet.getArray(1).getArray();
        return List.of(purposes);
      }
    }
  }

  private static int labelChunkCount(UUID documentId) throws Exception {
    return scalar(
        "SELECT count(*) FROM document_chunk WHERE document_id = ? AND kind = 'label'",
        documentId,
        ResultSet::getInt);
  }

  private interface ColumnReader<T> {
    T read(ResultSet resultSet, int column) throws Exception;
  }

  private static <T> T scalar(String sql, UUID documentId, ColumnReader<T> reader)
      throws Exception {
    try (Connection connection = jdbcConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, documentId);
      try (ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        return reader.read(resultSet, 1);
      }
    }
  }

  private static void truncateDocumentAndClientTables() throws Exception {
    try (Connection connection = jdbcConnection();
        var statement = connection.createStatement()) {
      statement.execute("TRUNCATE client CASCADE");
    }
  }

  private static Connection jdbcConnection() throws Exception {
    return DriverManager.getConnection(
        database.getJdbcUrl(), database.getUsername(), database.getPassword());
  }
}
