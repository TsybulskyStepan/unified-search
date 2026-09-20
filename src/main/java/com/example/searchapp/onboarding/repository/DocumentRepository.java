package com.example.searchapp.onboarding.repository;

import com.example.searchapp.onboarding.entity.Document;
import com.example.searchapp.onboarding.entity.SummaryStatus;
import com.example.searchapp.onboarding.service.Chunk;
import com.example.searchapp.onboarding.service.Classification;
import com.example.searchapp.onboarding.service.EmbeddedChunk;
import com.example.searchapp.onboarding.service.ReclassifiedRow;
import com.pgvector.PGvector;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
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
  private static final String DOCUMENT_COLUMNS =
      """
      id, client_id, title, content, summary, summary_status,
      document_type, purposes, classification_source, created_at
      """;

  private static final String INSERT_CHUNK_SQL =
      """
      INSERT INTO document_chunk
          (document_id, embedding_model, kind, ordinal, start_offset, end_offset, embedding)
      VALUES
          (:document_id, :embedding_model, :kind, :ordinal, :start_offset, :end_offset, :embedding)
      """;

  /**
   * The label chunk's ordinal (§5.2). Body chunks are persisted at {@code ordinal + 1} so the two
   * kinds never share an ordinal even though {@code kind} alone already makes the primary key
   * unique — a reader scanning {@code document_chunk} for one document sees ordinal 0 mean "the
   * label" everywhere, not just for documents with no body chunk at ordinal 0.
   */
  private static final int LABEL_ORDINAL = 0;

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
   * Inserts the document row, its label chunk and every body chunk embedding in one transaction
   * (§5.2): a document row, its label and ≥ 1 body chunk either all commit or none does, so a
   * document is never left unsearchable or half-labelled.
   */
  @Transactional
  public Document insert(
      UUID clientId,
      String title,
      String content,
      Classification classification,
      float[] labelEmbedding,
      List<EmbeddedChunk> bodyChunks,
      String embeddingModel) {
    Document document =
        jdbc.sql(
                """
                INSERT INTO document (client_id, title, content, summary_status,
                    document_type, purposes, classification_source, taxonomy_version, label_text)
                VALUES (:client_id, :title, :content, :summary_status,
                    :document_type, :purposes, :classification_source, :taxonomy_version, :label_text)
                RETURNING %s
                """
                    .formatted(DOCUMENT_COLUMNS))
            .param("client_id", clientId)
            .param("title", title)
            .param("content", content)
            .param("summary_status", SummaryStatus.NONE)
            .param("document_type", classification.documentType())
            .param("purposes", classification.purposes().toArray(String[]::new))
            .param("classification_source", classification.source())
            .param("taxonomy_version", classification.taxonomyVersion())
            .param("label_text", classification.labelText())
            .query(DocumentRepository::map)
            .single();

    SqlParameterSource[] batchParams =
        chunkBatchParams(document.id(), embeddingModel, labelEmbedding, bodyChunks);
    batchJdbc.batchUpdate(INSERT_CHUNK_SQL, batchParams);

    return document;
  }

  public Optional<Document> findById(UUID clientId, UUID documentId) {
    return jdbc.sql(
            """
            SELECT %s
            FROM document
            WHERE id = :document_id AND client_id = :client_id
            """
                .formatted(DOCUMENT_COLUMNS))
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
            RETURNING %s
            """
                .formatted(DOCUMENT_COLUMNS))
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

  /**
   * Rows a taxonomy version bump left behind, oldest first (§3.4): enough for {@code Reclassifier}
   * to either re-run rule classification or keep an existing request-supplied type.
   */
  public List<StaleDocument> findStale(int currentTaxonomyVersion, int limit) {
    return jdbc.sql(
            """
            SELECT id, title, content, document_type, purposes, classification_source
            FROM document
            WHERE taxonomy_version < :current_version
            ORDER BY created_at
            LIMIT :limit
            """)
        .param("current_version", currentTaxonomyVersion)
        .param("limit", limit)
        .query(DocumentRepository::mapStale)
        .list();
  }

  /**
   * Refreshes every row in one batch's labels and version, and rewrites each one's label chunk, in
   * a single transaction (§3.4): a crash partway through leaves every row in the batch either
   * already refreshed or still correctly flagged stale, never in between. The only internal write
   * to an existing row this system makes (§2.2); body chunks are untouched, since only what a
   * document is labelled — never its content — can go stale.
   */
  @Transactional
  public void reclassifyBatch(List<ReclassifiedRow> rows, String embeddingModel) {
    for (ReclassifiedRow row : rows) {
      reclassifyOne(row.documentId(), row.classification(), embeddingModel, row.labelEmbedding());
    }
  }

  private void reclassifyOne(
      UUID documentId,
      Classification classification,
      String embeddingModel,
      float[] labelEmbedding) {
    jdbc.sql(
            """
            UPDATE document
            SET document_type = :document_type, purposes = :purposes,
                classification_source = :classification_source,
                taxonomy_version = :taxonomy_version, label_text = :label_text
            WHERE id = :document_id
            """)
        .param("document_id", documentId)
        .param("document_type", classification.documentType())
        .param("purposes", classification.purposes().toArray(String[]::new))
        .param("classification_source", classification.source())
        .param("taxonomy_version", classification.taxonomyVersion())
        .param("label_text", classification.labelText())
        .update();

    // Upsert rather than plain UPDATE: a row from before this schema shipped never had a label
    // chunk at all, so the first reclassification pass over it must create one, not silently no-op.
    jdbc.sql(
            """
            INSERT INTO document_chunk
                (document_id, embedding_model, kind, ordinal, start_offset, end_offset, embedding)
            VALUES (:document_id, :embedding_model, 'label', :ordinal, 0, 0, :embedding)
            ON CONFLICT (document_id, embedding_model, kind, ordinal)
            DO UPDATE SET embedding = EXCLUDED.embedding
            """)
        .param("document_id", documentId)
        .param("embedding_model", embeddingModel)
        .param("ordinal", LABEL_ORDINAL)
        .param("embedding", new PGvector(labelEmbedding))
        .update();
  }

  private static SqlParameterSource[] chunkBatchParams(
      UUID documentId,
      String embeddingModel,
      float[] labelEmbedding,
      List<EmbeddedChunk> bodyChunks) {
    List<SqlParameterSource> params = new ArrayList<>(bodyChunks.size() + 1);
    params.add(
        chunkParams(documentId, embeddingModel, "label", LABEL_ORDINAL, 0, 0, labelEmbedding));
    for (EmbeddedChunk embeddedChunk : bodyChunks) {
      Chunk chunk = embeddedChunk.chunk();
      params.add(
          chunkParams(
              documentId,
              embeddingModel,
              "body",
              chunk.ordinal() + 1,
              chunk.startOffset(),
              chunk.endOffset(),
              embeddedChunk.embedding()));
    }
    return params.toArray(SqlParameterSource[]::new);
  }

  private static SqlParameterSource chunkParams(
      UUID documentId,
      String embeddingModel,
      String kind,
      int ordinal,
      int startOffset,
      int endOffset,
      float[] embedding) {
    return new MapSqlParameterSource()
        .addValue("document_id", documentId)
        .addValue("embedding_model", embeddingModel)
        .addValue("kind", kind)
        .addValue("ordinal", ordinal)
        .addValue("start_offset", startOffset)
        .addValue("end_offset", endOffset)
        .addValue("embedding", new PGvector(embedding));
  }

  private static Document map(ResultSet resultSet, int rowNumber) throws SQLException {
    return new Document(
        resultSet.getObject("id", UUID.class),
        resultSet.getObject("client_id", UUID.class),
        resultSet.getString("title"),
        resultSet.getString("content"),
        resultSet.getString("summary"),
        resultSet.getString("summary_status"),
        resultSet.getString("document_type"),
        List.of(purposesArray(resultSet)),
        resultSet.getString("classification_source"),
        resultSet.getTimestamp("created_at").toInstant());
  }

  private static StaleDocument mapStale(ResultSet resultSet, int rowNumber) throws SQLException {
    return new StaleDocument(
        resultSet.getObject("id", UUID.class),
        resultSet.getString("title"),
        resultSet.getString("content"),
        resultSet.getString("document_type"),
        List.of(purposesArray(resultSet)),
        resultSet.getString("classification_source"));
  }

  private static String[] purposesArray(ResultSet resultSet) throws SQLException {
    return resultSet.getArray("purposes") == null
        ? new String[0]
        : (String[]) resultSet.getArray("purposes").getArray();
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
