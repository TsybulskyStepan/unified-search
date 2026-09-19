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
}
