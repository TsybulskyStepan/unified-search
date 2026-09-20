package com.example.searchapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

class HealthAndSchemaIntegrationTest extends IntegrationTest {
  @LocalServerPort private int port;

  @Autowired private JdbcClient jdbcClient;

  @Test
  void healthReportsUpWhenTheDatabaseIsReachable() throws Exception {
    var response = get(port, "/health");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"status\":\"UP\"");
  }

  @Test
  void flywayCreatesTheExtensionsAndTablesUsedByUnifiedSearch() {
    List<String> extensions =
        jdbcClient
            .sql(
                "SELECT extname FROM pg_extension WHERE extname IN ('vector', 'pg_trgm', 'citext')")
            .query(String.class)
            .list();
    List<String> tables =
        jdbcClient
            .sql("SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename")
            .query(String.class)
            .list();
    List<String> primaryKeyColumns =
        jdbcClient
            .sql(
                """
                SELECT column_name
                FROM information_schema.key_column_usage
                WHERE constraint_name = 'document_chunk_pkey'
                ORDER BY ordinal_position
                """)
            .query(String.class)
            .list();
    List<String> embeddingColumns =
        jdbcClient
            .sql(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_name = 'document_chunk'
                  AND column_name IN ('embedding', 'embedding_768')
                ORDER BY ordinal_position
                """)
            .query(String.class)
            .list();

    assertThat(extensions).containsExactlyInAnyOrder("vector", "pg_trgm", "citext");
    assertThat(tables).contains("client", "document", "document_chunk");
    assertThat(primaryKeyColumns).containsExactly("document_id", "embedding_model", "ordinal");
    assertThat(embeddingColumns).containsExactly("embedding", "embedding_768");
  }
}
