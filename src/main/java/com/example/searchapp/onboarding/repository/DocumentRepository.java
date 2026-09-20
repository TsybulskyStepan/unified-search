package com.example.searchapp.onboarding.repository;

import com.example.searchapp.onboarding.entity.Document;
import com.example.searchapp.onboarding.entity.SummaryStatus;
import com.example.searchapp.onboarding.service.Chunk;
import com.example.searchapp.onboarding.service.EmbeddedChunk;
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
  private static final String INSERT_CHUNK_SQL =
      """
      INSERT INTO document_chunk
          (document_id, embedding_model, ordinal, start_offset, end_offset, embedding_768)
      VALUES
          (:document_id, :embedding_model, :ordinal, :start_offset, :end_offset, :embedding_768)
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
            .param("summary_status", SummaryStatus.NONE)
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

  /** Idempotent request transition (§7.2). Scoped by {@code client_id} too, defensively. */
  public Optional<Document> requestSummary(UUID clientId, UUID documentId) {
    return jdbc.sql(
            """
            UPDATE document
            SET summary_status = :pending, summary_attempts = 0, summary_lease_until = NULL
            WHERE id = :document_id AND client_id = :client_id
              AND summary_status IN (:none, :failed)
            RETURNING id, client_id, title, content, summary, summary_status, created_at
            """)
        .param("document_id", documentId)
        .param("client_id", clientId)
        .param("pending", SummaryStatus.PENDING)
        .param("none", SummaryStatus.NONE)
        .param("failed", SummaryStatus.FAILED)
        .query(DocumentRepository::map)
        .optional();
  }

  /** Claims up to {@code limit} pending rows under a lease (§7.2). */
  public List<ClaimedSummaryJob> claimPending(int limit) {
    return jdbc.sql(
            """
            UPDATE document
            SET summary_attempts = summary_attempts + 1,
                summary_lease_until = now() + interval '2 minutes'
            WHERE id IN (
                SELECT id FROM document
                WHERE summary_status = :pending
                  AND summary_attempts < 3
                  AND (summary_lease_until IS NULL OR summary_lease_until < now())
                ORDER BY created_at
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            )
            RETURNING id, title, content, summary_attempts
            """)
        .param("limit", limit)
        .param("pending", SummaryStatus.PENDING)
        .query(DocumentRepository::mapClaimedJob)
        .list();
  }

  /** Completes a claimed row successfully (§7.2). */
  public void completeSummarySuccess(UUID documentId, String summary) {
    jdbc.sql(
            """
            UPDATE document SET summary = :summary, summary_status = :ready,
                summary_lease_until = NULL
            WHERE id = :document_id AND summary_status = :pending
            """)
        .param("summary", summary)
        .param("document_id", documentId)
        .param("ready", SummaryStatus.READY)
        .param("pending", SummaryStatus.PENDING)
        .update();
  }

  /** Fails a claimed row immediately, without waiting on attempts (§7.2). */
  public void completeSummaryFailed(UUID documentId) {
    jdbc.sql(
            """
            UPDATE document SET summary_status = :failed, summary_lease_until = NULL
            WHERE id = :document_id AND summary_status = :pending
            """)
        .param("document_id", documentId)
        .param("failed", SummaryStatus.FAILED)
        .param("pending", SummaryStatus.PENDING)
        .update();
  }

  /** Sweep step: exhausted rows move to {@code failed} (§7.2). */
  public int markExhaustedAsFailed() {
    return jdbc.sql(
            """
            UPDATE document
            SET summary_status = :failed, summary_lease_until = NULL
            WHERE summary_status = :pending
              AND summary_attempts >= 3
              AND (summary_lease_until IS NULL OR summary_lease_until < now())
            """)
        .param("failed", SummaryStatus.FAILED)
        .param("pending", SummaryStatus.PENDING)
        .update();
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
        .addValue("embedding_768", new PGvector(embeddedChunk.embedding()));
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

  private static ClaimedSummaryJob mapClaimedJob(ResultSet resultSet, int rowNumber)
      throws SQLException {
    return new ClaimedSummaryJob(
        resultSet.getObject("id", UUID.class),
        resultSet.getString("title"),
        resultSet.getString("content"),
        resultSet.getInt("summary_attempts"));
  }
}
