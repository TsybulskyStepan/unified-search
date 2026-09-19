package com.example.searchapp.search.repository;

import com.example.searchapp.search.entity.SearchDocument;
import com.pgvector.PGvector;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentSearchRepository {
  private final JdbcClient jdbc;
  private final double semanticFloor;

  public DocumentSearchRepository(
      JdbcClient jdbc, @Value("${app.search.semantic-floor}") double semanticFloor) {
    this.jdbc = jdbc;
    this.semanticFloor = semanticFloor;
  }

  public List<DocumentMatch> findMatches(float[] queryVector, String embeddingModel) {
    return jdbc.sql(
            """
            SELECT document_id, client_id, start_offset, end_offset, similarity
            FROM (
                SELECT DISTINCT ON (document_id)
                    document_id,
                    client_id,
                    start_offset,
                    end_offset,
                    1 - (embedding <=> :query_vector) AS similarity
                FROM document_chunk chunk
                JOIN document ON document.id = chunk.document_id
                WHERE embedding_model = :embedding_model
                ORDER BY document_id, embedding <=> :query_vector
            ) AS best_chunk
            WHERE similarity >= :semantic_floor
            ORDER BY similarity DESC, document_id
            LIMIT 200
            """)
        .param("query_vector", new PGvector(queryVector))
        .param("embedding_model", embeddingModel)
        .param("semantic_floor", semanticFloor)
        .query(DocumentSearchRepository::mapMatch)
        .list();
  }

  public List<HydratedDocument> findByMatches(List<DocumentMatch> matches) {
    if (matches.isEmpty()) {
      return List.of();
    }

    UUID[] documentIds = matches.stream().map(DocumentMatch::documentId).toArray(UUID[]::new);
    int[] starts = matches.stream().mapToInt(DocumentMatch::startOffset).toArray();
    int[] ends = matches.stream().mapToInt(DocumentMatch::endOffset).toArray();

    return jdbc.sql(
            """
            SELECT d.id,
                   d.client_id,
                   c.first_name || ' ' || c.last_name AS client_name,
                   d.title,
                   d.summary,
                   d.summary_status,
                   d.created_at,
                   substr(d.content, p.start_offset + 1, p.end_offset - p.start_offset) AS passage
            FROM unnest(
                CAST(:document_ids AS uuid[]),
                CAST(:starts AS int[]),
                CAST(:ends AS int[])
            ) AS p(id, start_offset, end_offset)
            JOIN document d ON d.id = p.id
            JOIN client c ON c.id = d.client_id
            """)
        .param("document_ids", documentIds)
        .param("starts", starts)
        .param("ends", ends)
        .query(DocumentSearchRepository::mapDocument)
        .list();
  }

  private static DocumentMatch mapMatch(ResultSet resultSet, int rowNumber) throws SQLException {
    return new DocumentMatch(
        resultSet.getObject("document_id", UUID.class),
        resultSet.getObject("client_id", UUID.class),
        resultSet.getInt("start_offset"),
        resultSet.getInt("end_offset"),
        resultSet.getDouble("similarity"));
  }

  private static HydratedDocument mapDocument(ResultSet resultSet, int rowNumber)
      throws SQLException {
    SearchDocument document =
        new SearchDocument(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("client_id", UUID.class),
            resultSet.getString("client_name"),
            resultSet.getString("title"),
            resultSet.getString("summary"),
            resultSet.getString("summary_status"),
            resultSet.getTimestamp("created_at").toInstant());
    return new HydratedDocument(document, resultSet.getString("passage"));
  }
}
