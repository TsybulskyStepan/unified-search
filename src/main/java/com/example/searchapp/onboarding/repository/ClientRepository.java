package com.example.searchapp.onboarding.repository;

import com.example.searchapp.onboarding.dto.CreateClientRequest;
import com.example.searchapp.onboarding.entity.Client;
import com.example.searchapp.onboarding.exception.DuplicateClientEmailException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ClientRepository {
  private final JdbcClient jdbc;

  public ClientRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Client insert(CreateClientRequest request) {
    try {
      return jdbc.sql(
              """
              INSERT INTO client (first_name, last_name, email, description, social_links)
              VALUES (:first_name, :last_name, :email, :description, :social_links)
              RETURNING id, first_name, last_name, email, description, social_links, created_at
              """)
          .param("first_name", request.firstName())
          .param("last_name", request.lastName())
          .param("email", request.email())
          .param("description", request.description())
          .param("social_links", request.socialLinks().toArray(String[]::new))
          .query(ClientRepository::map)
          .single();
    } catch (DataIntegrityViolationException exception) {
      if (isEmailUniquenessViolation(exception)) {
        throw new DuplicateClientEmailException();
      }
      throw exception;
    }
  }

  public Optional<Client> findById(UUID id) {
    return jdbc.sql(
            """
            SELECT id, first_name, last_name, email, description, social_links, created_at
            FROM client
            WHERE id = :id
            """)
        .param("id", id)
        .query(ClientRepository::map)
        .optional();
  }

  private static Client map(ResultSet resultSet, int rowNumber) throws SQLException {
    String[] socialLinks =
        resultSet.getArray("social_links") == null
            ? new String[0]
            : (String[]) resultSet.getArray("social_links").getArray();
    return new Client(
        resultSet.getObject("id", UUID.class),
        resultSet.getString("first_name"),
        resultSet.getString("last_name"),
        resultSet.getString("email"),
        resultSet.getString("description"),
        List.of(socialLinks),
        resultSet.getTimestamp("created_at").toInstant());
  }

  private static boolean isEmailUniquenessViolation(Throwable exception) {
    for (Throwable current = exception; current != null; current = current.getCause()) {
      if (current.getMessage() != null && current.getMessage().contains("client_email_uk")) {
        return true;
      }
    }
    return false;
  }
}
