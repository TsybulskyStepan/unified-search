package com.example.searchapp.search.repository;

import com.example.searchapp.search.entity.SearchDocument;
import com.example.searchapp.search.repository.model.DocumentMatch;
import com.example.searchapp.search.repository.model.HydratedDocument;
import com.example.searchapp.search.repository.model.LabelDocumentMatch;
import com.example.searchapp.search.repository.model.RankedDocumentMatch;
import com.pgvector.PGvector;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentSearchRepository {
  private static final int CANDIDATE_LIMIT = 200;

  // The word still being typed: letters only, so digits and operators keep websearch semantics.
  private static final Pattern PREFIX_TERM = Pattern.compile("\\p{L}{3,}");

  private final JdbcClient jdbc;
  private final double semanticFloor;

  public DocumentSearchRepository(
      JdbcClient jdbc, @Value("${app.search.semantic-floor}") double semanticFloor) {
    this.jdbc = jdbc;
    this.semanticFloor = semanticFloor;
  }

  public List<LabelDocumentMatch> findLabelMatches(Set<String> types, Set<String> purposes) {
    if (types.isEmpty() && purposes.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            """
            SELECT id, client_id, document_type, purposes, created_at
            FROM document
            WHERE document_type = ANY(:types) OR purposes && CAST(:purposes AS text[])
            ORDER BY created_at DESC, id
            LIMIT :limit
            """)
        .param("types", types.toArray(String[]::new))
        .param("purposes", purposes.toArray(String[]::new))
        .param("limit", CANDIDATE_LIMIT)
        .query((resultSet, rowNumber) -> mapLabelMatch(resultSet, types, purposes))
        .list();
  }

  public boolean hasSearchableTerms(String residual) {
    return jdbc.sql("SELECT numnode(websearch_to_tsquery('english', :residual)) > 0")
        .param("residual", residual)
        .query(Boolean.class)
        .single();
  }

  public List<RankedDocumentMatch> findLexicalMatches(String residual) {
    int tailStart = residual.lastIndexOf(' ') + 1;
    String tail = residual.substring(tailStart);
    boolean prefixTail = PREFIX_TERM.matcher(tail).matches();
    String head = prefixTail ? residual.substring(0, tailStart) : residual;
    return jdbc.sql(
            """
            WITH query AS (
                SELECT websearch_to_tsquery('english', :head)
                       && CASE WHEN :prefix = '' THEN ''::tsquery
                               ELSE to_tsquery('english', :prefix || ':*') END AS tq
            )
            SELECT d.id AS document_id, d.client_id, d.created_at, ts_rank_cd(d.tsv, query.tq, 32) AS similarity
            FROM document d, query
            WHERE numnode(query.tq) > 0 AND d.tsv @@ query.tq
            ORDER BY similarity DESC, d.id
            LIMIT :limit
            """)
        .param("head", head)
        .param("prefix", prefixTail ? tail : "")
        .param("limit", CANDIDATE_LIMIT)
        .query(DocumentSearchRepository::mapRankedMatch)
        .list();
  }

  public List<RankedDocumentMatch> findSemanticMatches(float[] queryVector, String embeddingModel) {
    return jdbc.sql(
            """
            SELECT document_id, client_id, created_at, similarity
            FROM (
                SELECT DISTINCT ON (chunk.document_id)
                    chunk.document_id,
                    document.client_id,
                    document.created_at,
                    1 - (chunk.embedding <=> :query_vector) AS similarity
                FROM document_chunk chunk
                JOIN document ON document.id = chunk.document_id
                WHERE chunk.embedding_model = :embedding_model
                ORDER BY chunk.document_id, chunk.embedding <=> :query_vector
            ) AS best_chunk
            WHERE similarity >= :semantic_floor
            ORDER BY similarity DESC, document_id
            LIMIT :limit
            """)
        .param("query_vector", new PGvector(queryVector))
        .param("embedding_model", embeddingModel)
        .param("semantic_floor", semanticFloor)
        .param("limit", CANDIDATE_LIMIT)
        .query(DocumentSearchRepository::mapRankedMatch)
        .list();
  }

  public List<HydratedDocument> findByMatches(
      List<DocumentMatch> matches, float[] queryVector, String embeddingModel) {
    if (matches.isEmpty()) {
      return List.of();
    }
    UUID[] documentIds = matches.stream().map(DocumentMatch::documentId).toArray(UUID[]::new);
    return jdbc.sql(
            """
            SELECT d.id,
                   d.client_id,
                   c.first_name || ' ' || c.last_name AS client_name,
                   d.title,
                   d.summary,
                   d.summary_status,
                   d.document_type,
                   d.purposes,
                   d.classification_source,
                   d.created_at,
                   substr(d.content, p.start_offset + 1, p.end_offset - p.start_offset) AS passage
            FROM unnest(CAST(:document_ids AS uuid[])) AS selected(id)
            JOIN document d ON d.id = selected.id
            JOIN client c ON c.id = d.client_id
            JOIN LATERAL (
                SELECT start_offset, end_offset
                FROM document_chunk
                WHERE document_id = d.id AND kind = 'body' AND embedding_model = :embedding_model
                ORDER BY embedding <=> :query_vector
                LIMIT 1
            ) p ON true
            """)
        .param("document_ids", documentIds)
        .param("embedding_model", embeddingModel)
        .param("query_vector", new PGvector(queryVector))
        .query(DocumentSearchRepository::mapDocument)
        .list();
  }

  private static LabelDocumentMatch mapLabelMatch(
      ResultSet resultSet, Set<String> types, Set<String> purposes) throws SQLException {
    String documentType = resultSet.getString("document_type");
    String[] documentPurposes = (String[]) resultSet.getArray("purposes").getArray();
    List<String> labels = new ArrayList<>();
    if (types.contains(documentType)) {
      labels.add("type:" + documentType);
    }
    for (String purpose : documentPurposes) {
      if (purposes.contains(purpose)) {
        labels.add("purpose:" + purpose);
      }
    }
    return new LabelDocumentMatch(
        resultSet.getObject("id", UUID.class),
        resultSet.getObject("client_id", UUID.class),
        resultSet.getTimestamp("created_at").toInstant(),
        labels);
  }

  private static RankedDocumentMatch mapRankedMatch(ResultSet resultSet, int rowNumber)
      throws SQLException {
    return new RankedDocumentMatch(
        resultSet.getObject("document_id", UUID.class),
        resultSet.getObject("client_id", UUID.class),
        resultSet.getTimestamp("created_at").toInstant(),
        resultSet.getDouble("similarity"));
  }

  private static HydratedDocument mapDocument(ResultSet resultSet, int rowNumber)
      throws SQLException {
    String[] purposes = (String[]) resultSet.getArray("purposes").getArray();
    SearchDocument document =
        new SearchDocument(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("client_id", UUID.class),
            resultSet.getString("client_name"),
            resultSet.getString("title"),
            resultSet.getString("summary"),
            resultSet.getString("summary_status"),
            resultSet.getString("document_type"),
            List.of(purposes),
            resultSet.getString("classification_source"),
            resultSet.getTimestamp("created_at").toInstant());
    return new HydratedDocument(document, resultSet.getString("passage"));
  }
}
