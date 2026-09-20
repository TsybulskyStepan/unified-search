package com.example.searchapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.searchapp.onboarding.dto.CreateClientRequest;
import com.example.searchapp.onboarding.repository.ClientRepository;
import com.example.searchapp.onboarding.repository.DocumentRepository;
import com.example.searchapp.onboarding.service.Chunk;
import com.example.searchapp.onboarding.service.EmbeddedChunk;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * A document row and all of its chunks commit in one transaction (§5.2); a failure part-way leaves
 * neither behind, so a document can never exist without being searchable.
 */
class DocumentRepositoryAtomicityIntegrationTest extends IntegrationTest {
  @Autowired private ClientRepository clients;
  @Autowired private DocumentRepository documents;
  @Autowired private JdbcClient jdbcClient;

  @Test
  void aFailingChunkInsertRollsBackTheDocumentRowToo() {
    var client =
        clients.insert(
            new CreateClientRequest("Atomicity", "Test", "atomicity.test@example.com", null, null));

    // The second chunk's embedding is the wrong dimension for the vector(384) column — pgvector
    // rejects the insert, which must take the document row down with it.
    List<EmbeddedChunk> chunks =
        List.of(
            new EmbeddedChunk(new Chunk(0, 0, 5), new float[384]),
            new EmbeddedChunk(new Chunk(1, 6, 11), new float[10]));

    assertThatThrownBy(
            () -> documents.insert(client.id(), "Title", "one two", chunks, "test-model"))
        .isInstanceOf(DataAccessException.class);

    Integer documentCount =
        jdbcClient
            .sql("SELECT count(*) FROM document WHERE client_id = :client_id")
            .param("client_id", client.id())
            .query(Integer.class)
            .single();
    assertThat(documentCount).isZero();
  }
}
