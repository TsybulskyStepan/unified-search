package com.example.searchapp.onboarding.document;

import com.pgvector.PGvector;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class DocumentRepository {
  private static final String NO_SUMMARY_REQUESTED = "none";
  private static final String INSERT_CHUNK_SQL =
      """
      INSERT INTO document_chunk
          (document_id, embedding_model, ordinal, start_offset, end_offset, embedding)
      VALUES
          (:document_id, :embedding_model, :ordinal, :start_offset, :end_offset, :embedding)
      """;

  private final JdbcClient jdbc;
  // JdbcClient has no batch-update support (§1.4); NamedParameterJdbcTemplate is the documented
  // escape hatch for the one place this repository needs it (§5.2: "batch INSERT document_chunk
  // × n"), still fully parameterized, still no JPA.
  private final NamedParameterJdbcTemplate batchJdbc;

  public DocumentRepository(JdbcClient jdbc, NamedParameterJdbcTemplate batchJdbc) {
    this.jdbc = jdbc;
    this.batchJdbc = batchJdbc;
  }

  /**
   * Inserts the document row and every chunk embedding in one transaction (§5.2): a document row
   * and its chunks either both commit or neither does, so a document is never left unsearchable.
   */
  @Transactional
  public Document insert(
      UUID clientId,
      String title,
      String content,
      List<EmbeddedChunk> chunks,
      String embeddingModel) {
    Document document =
        jdbc.sql(
                """
                INSERT INTO document (client_id, title, content, summary_status)
                VALUES (:client_id, :title, :content, :summary_status)
                RETURNING id, client_id, title, content, summary, summary_status, created_at
                """)
            .param("client_id", clientId)
            .param("title", title)
            .param("content", content)
            .param("summary_status", NO_SUMMARY_REQUESTED)
            .query(DocumentRepository::map)
            .single();

    SqlParameterSource[] batchParams =
        chunks.stream()
            .map(embeddedChunk -> chunkParams(document.id(), embeddingModel, embeddedChunk))
            .toArray(SqlParameterSource[]::new);
    batchJdbc.batchUpdate(INSERT_CHUNK_SQL, batchParams);

    return document;
  }

  public Optional<Document> findById(UUID clientId, UUID documentId) {
    return jdbc.sql(
            """
            SELECT id, client_id, title, content, summary, summary_status, created_at
            FROM document
            WHERE id = :document_id AND client_id = :client_id
            """)
        .param("document_id", documentId)
        .param("client_id", clientId)
        .query(DocumentRepository::map)
        .optional();
  }

  private static SqlParameterSource chunkParams(
      UUID documentId, String embeddingModel, EmbeddedChunk embeddedChunk) {
    Chunk chunk = embeddedChunk.chunk();
    return new MapSqlParameterSource()
        .addValue("document_id", documentId)
        .addValue("embedding_model", embeddingModel)
        .addValue("ordinal", chunk.ordinal())
        .addValue("start_offset", chunk.startOffset())
        .addValue("end_offset", chunk.endOffset())
        .addValue("embedding", new PGvector(embeddedChunk.embedding()));
  }

  private static Document map(ResultSet resultSet, int rowNumber) throws SQLException {
    return new Document(
        resultSet.getObject("id", UUID.class),
        resultSet.getObject("client_id", UUID.class),
        resultSet.getString("title"),
        resultSet.getString("content"),
        resultSet.getString("summary"),
        resultSet.getString("summary_status"),
        resultSet.getTimestamp("created_at").toInstant());
  }
}
