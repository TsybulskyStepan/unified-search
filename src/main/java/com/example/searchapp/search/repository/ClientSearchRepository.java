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
            SELECT c.id, best.field, best.tier, best.score
            FROM client c
            CROSS JOIN LATERAL (
                SELECT field, tier, score
                FROM (VALUES
                    ('name',         'identity', word_similarity(:query, c.first_name || ' ' || c.last_name)),
                    ('email',        'identity', word_similarity(:query, c.email::text)),
                    ('social_links', 'identity', word_similarity(:query, array_to_string(c.social_links, ' '))),
                    ('description',  'context',  word_similarity(:query, coalesce(c.description, '')))
                ) AS fields(field, tier, score)
                WHERE score >= :lexicalFloor
                ORDER BY (tier = 'identity') DESC, score DESC
                LIMIT 1
            ) best
            ORDER BY (best.tier = 'identity') DESC, best.score DESC, c.last_name, c.id
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
                WHERE char_length(token) >= 3
            ), token_scores AS (
                SELECT c.id, query_tokens.position, best.field, best.score
                FROM client c
                CROSS JOIN query_tokens
                CROSS JOIN LATERAL (
                    SELECT field, word_similarity(query_tokens.token, value) AS score
                    FROM (VALUES
                        ('name', c.first_name || ' ' || c.last_name),
                        ('email', c.email::text)
                    ) AS fields(field, value)
                    ORDER BY score DESC, field
                    LIMIT 1
                ) best
            ), runs AS (
                SELECT id, position, field, score,
                       bool_and(score >= :mention_floor)
                           OVER (PARTITION BY id ORDER BY position) AS in_leading_run
                FROM token_scores
            ), leading_runs AS (
                SELECT id,
                       (array_agg(field ORDER BY score DESC, field))[1] AS field,
                       max(score) AS score,
                       max(position) AS matched_through_position
                FROM runs
                WHERE in_leading_run
                GROUP BY id
            )
            SELECT leading_runs.id, leading_runs.field, leading_runs.score,
                   leading_runs.matched_through_position
            FROM leading_runs
            JOIN client c ON c.id = leading_runs.id
            WHERE matched_through_position = (SELECT max(matched_through_position) FROM leading_runs)
            ORDER BY leading_runs.score DESC,
                     greatest(
                         word_similarity(:query, c.first_name || ' ' || c.last_name),
                         word_similarity(:query, c.email::text)) DESC,
                     c.last_name, c.id
            """)
        .param("query", String.join(" ", tokens))
        .param("tokens", queryTokens)
        .param("mention_floor", MENTION_FLOOR)
        .query(ClientSearchRepository::mapMention)
        .list();
  }

  private static ClientMatch map(ResultSet resultSet, int rowNumber) throws SQLException {
    return new ClientMatch(
        resultSet.getObject("id", UUID.class),
        resultSet.getString("field"),
        resultSet.getString("tier"),
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
