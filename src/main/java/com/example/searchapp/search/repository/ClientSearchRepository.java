package com.example.searchapp.search.repository;

import com.example.searchapp.search.entity.SearchClient;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ClientSearchRepository {
  private static final double LEXICAL_FLOOR = 0.6;

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

  public List<ClientMention> findMentions(List<String> tokens) {
    if (tokens.isEmpty()) {
      return List.of();
    }
    String[] distinctTokens = tokens.stream().distinct().toArray(String[]::new);
    return jdbc.sql(
            """
            WITH token_scores AS (
                SELECT c.id, token, field, word_similarity(token, value) AS score
                FROM client c
                CROSS JOIN unnest(CAST(:tokens AS text[])) AS query_tokens(token)
                CROSS JOIN LATERAL (VALUES
                    ('name', c.first_name || ' ' || c.last_name),
                    ('email', c.email::text)
                ) AS fields(field, value)
            ), token_matches AS (
                SELECT DISTINCT ON (id, token) id, token, field, score
                FROM token_scores
                WHERE score >= :mention_floor
                ORDER BY id, token, score DESC, field
            )
            SELECT id,
                   (array_agg(field ORDER BY score DESC, field))[1] AS field,
                   max(score) AS score,
                   count(*) AS matched_token_count
            FROM token_matches
            GROUP BY id
            ORDER BY max(score) DESC, id
            LIMIT 2
            """)
        .param("tokens", distinctTokens)
        .param("mention_floor", 0.7)
        .query((resultSet, rowNumber) -> mapMention(resultSet, distinctTokens.length))
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

  private static ClientMention mapMention(ResultSet resultSet, int tokenCount) throws SQLException {
    return new ClientMention(
        resultSet.getObject("id", UUID.class),
        resultSet.getString("field"),
        resultSet.getDouble("score"),
        resultSet.getInt("matched_token_count") < tokenCount);
  }
}
