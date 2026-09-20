package com.example.searchapp.search.repository;

import com.example.searchapp.search.entity.SearchClient;
import com.example.searchapp.search.planner.MentionCandidate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ClientSearchRepository {
  private static final double LEXICAL_FLOOR = 0.6;
  private static final double MENTION_FLOOR = 0.69;

  private final JdbcClient jdbc;

  public ClientSearchRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public List<ClientMatch> findMatches(String query) {
    return jdbc.sql(
            """
            SELECT c.id, best.field, best.score
            FROM client c
            CROSS JOIN LATERAL (
                SELECT field, score
                FROM (VALUES
                    ('name',         word_similarity(:query, c.first_name || ' ' || c.last_name)),
                    ('email',        word_similarity(:query, c.email::text)),
                    ('description',  word_similarity(:query, coalesce(c.description, ''))),
                    ('social_links', word_similarity(:query, array_to_string(c.social_links, ' ')))
                ) AS fields(field, score)
                ORDER BY score DESC
                LIMIT 1
            ) best
            WHERE best.score >= :lexicalFloor
            ORDER BY best.score DESC, c.last_name, c.id
            LIMIT 200
            """)
        .param("query", query)
        .param("lexicalFloor", LEXICAL_FLOOR)
        .query(ClientSearchRepository::map)
        .list();
  }

  public List<SearchClient> findByIds(List<UUID> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            """
            SELECT id, first_name, last_name, email, description, social_links, created_at
            FROM client
            WHERE id IN (:ids)
            """)
        .param("ids", ids)
        .query(ClientSearchRepository::mapClient)
        .list();
  }

  public List<MentionCandidate> findMentionCandidates(List<String> tokens) {
    if (tokens.isEmpty()) {
      return List.of();
    }
    String[] queryTokens = tokens.toArray(String[]::new);
    return jdbc.sql(
            """
            WITH query_tokens AS (
                SELECT token, position
                FROM unnest(CAST(:tokens AS text[])) WITH ORDINALITY AS query_tokens(token, position)
            ), token_scores AS (
                SELECT c.id, query_tokens.position, field,
                       word_similarity(query_tokens.token, value) AS score
                FROM client c
                CROSS JOIN query_tokens
                CROSS JOIN LATERAL (VALUES
                    ('name', c.first_name || ' ' || c.last_name),
                    ('email', c.email::text)
                ) AS fields(field, value)
                WHERE char_length(query_tokens.token) >= 3
            ), token_matches AS (
                SELECT DISTINCT ON (id, position) id, position, field, score
                FROM token_scores
                WHERE score >= :mention_floor
                ORDER BY id, position, score DESC, field
            ), first_non_identity_token AS (
                SELECT c.id,
                       coalesce(min(query_tokens.position), :token_count + 1) AS position
                FROM client c
                CROSS JOIN query_tokens
                WHERE char_length(query_tokens.token) >= 3
                  AND NOT EXISTS (
                    SELECT 1
                    FROM token_matches
                    WHERE token_matches.id = c.id
                      AND token_matches.position = query_tokens.position
                )
                GROUP BY c.id
            )
            SELECT token_matches.id,
                   (array_agg(field ORDER BY score DESC, field))[1] AS field,
                   max(score) AS score,
                   max(token_matches.position) AS matched_through_position
            FROM token_matches
            JOIN first_non_identity_token
              ON first_non_identity_token.id = token_matches.id
            WHERE token_matches.position < first_non_identity_token.position
            GROUP BY token_matches.id
            ORDER BY max(score) DESC, id
            LIMIT 2
            """)
        .param("tokens", queryTokens)
        .param("mention_floor", MENTION_FLOOR)
        .param("token_count", queryTokens.length)
        .query(ClientSearchRepository::mapMention)
        .list();
  }

  private static ClientMatch map(ResultSet resultSet, int rowNumber) throws SQLException {
    return new ClientMatch(
        resultSet.getObject("id", UUID.class),
        resultSet.getString("field"),
        resultSet.getDouble("score"));
  }

  private static SearchClient mapClient(ResultSet resultSet, int rowNumber) throws SQLException {
    String[] socialLinks =
        resultSet.getArray("social_links") == null
            ? new String[0]
            : (String[]) resultSet.getArray("social_links").getArray();
    return new SearchClient(
        resultSet.getObject("id", UUID.class),
        resultSet.getString("first_name"),
        resultSet.getString("last_name"),
        resultSet.getString("email"),
        resultSet.getString("description"),
        List.of(socialLinks),
        resultSet.getTimestamp("created_at").toInstant());
  }

  private static MentionCandidate mapMention(ResultSet resultSet, int rowNumber)
      throws SQLException {
    return new MentionCandidate(
        resultSet.getObject("id", UUID.class),
        resultSet.getString("field"),
        resultSet.getDouble("score"),
        resultSet.getInt("matched_through_position"));
  }
}
