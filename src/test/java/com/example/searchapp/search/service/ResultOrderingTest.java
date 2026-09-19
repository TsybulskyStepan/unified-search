package com.example.searchapp.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.searchapp.search.repository.ClientMatch;
import com.example.searchapp.search.repository.ClientMention;
import com.example.searchapp.search.repository.DocumentMatch;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResultOrderingTest {
  private static final UUID JOHN = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID MARY = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID JOHNS_BILL = UUID.fromString("00000000-0000-0000-0000-000000000011");
  private static final UUID MARYS_BILL = UUID.fromString("00000000-0000-0000-0000-000000000012");

  @Test
  void promotesTheOneMentionedClientsQualifiedDocumentsThenTheClient() {
    var candidates =
        ResultOrdering.order(
            List.of(client(MARY), client(JOHN)),
            List.of(document(MARYS_BILL, MARY), document(JOHNS_BILL, JOHN)),
            List.of(new ClientMention(JOHN, "name", 1.0, true)));

    assertThat(candidates)
        .extracting(ResultOrdering.Candidate::id)
        .containsExactly(JOHNS_BILL, JOHN, MARY, MARYS_BILL);
  }

  @Test
  void keepsDefaultOrderingWhenTheMentionHasNoResidualTerm() {
    var candidates =
        ResultOrdering.order(
            List.of(client(JOHN)),
            List.of(document(JOHNS_BILL, JOHN)),
            List.of(new ClientMention(JOHN, "name", 1.0, false)));

    assertThat(candidates)
        .extracting(ResultOrdering.Candidate::id)
        .containsExactly(JOHN, JOHNS_BILL);
  }

  @Test
  void keepsDefaultOrderingWhenTheMentionIsAmbiguousOrHasNoQualifiedDocument() {
    var clients = List.of(client(MARY), client(JOHN));
    var documents = List.of(document(MARYS_BILL, MARY));

    assertThat(
            ResultOrdering.order(
                clients,
                documents,
                List.of(
                    new ClientMention(JOHN, "name", 1.0, true),
                    new ClientMention(MARY, "name", 1.0, true))))
        .extracting(ResultOrdering.Candidate::id)
        .containsExactly(MARY, JOHN, MARYS_BILL);
    assertThat(
            ResultOrdering.order(
                clients, documents, List.of(new ClientMention(JOHN, "name", 1.0, true))))
        .extracting(ResultOrdering.Candidate::id)
        .containsExactly(MARY, JOHN, MARYS_BILL);
  }

  private static ClientMatch client(UUID id) {
    return new ClientMatch(id, "name", 1.0);
  }

  private static DocumentMatch document(UUID documentId, UUID clientId) {
    return new DocumentMatch(documentId, clientId, 0, 1, 1.0);
  }
}
