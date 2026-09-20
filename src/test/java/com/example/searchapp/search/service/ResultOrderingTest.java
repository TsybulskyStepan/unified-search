package com.example.searchapp.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.searchapp.search.planner.ClientMention;
import com.example.searchapp.search.planner.QueryPlan;
import com.example.searchapp.search.repository.ClientMatch;
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
            plan(JOHN, "utility bill"));

    assertThat(candidates)
        .extracting(ResultOrdering.Candidate::id)
        .containsExactly(JOHNS_BILL, JOHN, MARYS_BILL, MARY);
  }

  @Test
  void keepsTheMentionedClientsDocumentsAheadOfTheRecognizedClient() {
    var candidates =
        ResultOrdering.order(
            List.of(client(JOHN)), List.of(document(JOHNS_BILL, JOHN)), plan(JOHN, ""));

    assertThat(candidates)
        .extracting(ResultOrdering.Candidate::id)
        .containsExactly(JOHNS_BILL, JOHN);
  }

  @Test
  void keepsDefaultOrderingForAmbiguousMentionsAndInsertsARecognizedClientWithoutDocuments() {
    var clients = List.of(client(MARY), client(JOHN));
    var documents = List.of(document(MARYS_BILL, MARY));

    assertThat(
            ResultOrdering.order(
                clients,
                documents,
                new QueryPlan("john mary", null, "john mary", java.util.Set.of())))
        .extracting(ResultOrdering.Candidate::id)
        .containsExactly(MARY, JOHN, MARYS_BILL);
    assertThat(ResultOrdering.order(clients, documents, plan(JOHN, "utility bill")))
        .extracting(ResultOrdering.Candidate::id)
        .containsExactly(JOHN, MARYS_BILL, MARY);
  }

  @Test
  void placesContextClientsAfterDocumentsWhileKeepingIdentityClientsFirst() {
    var candidates =
        ResultOrdering.order(
            List.of(client(JOHN, "identity"), client(MARY, "context")),
            List.of(document(MARYS_BILL, MARY)),
            new QueryPlan("advisory fees", null, "advisory fees", java.util.Set.of()));

    assertThat(candidates)
        .extracting(ResultOrdering.Candidate::id)
        .containsExactly(JOHN, MARYS_BILL, MARY);
  }

  private static ClientMatch client(UUID id) {
    return client(id, "identity");
  }

  private static ClientMatch client(UUID id, String tier) {
    return new ClientMatch(id, "name", tier, 1.0);
  }

  private static DocumentMatch document(UUID documentId, UUID clientId) {
    return new DocumentMatch(documentId, clientId, 1.0, List.of("semantic"), List.of());
  }

  private static QueryPlan plan(UUID clientId, String residual) {
    return new QueryPlan(
        residual.isEmpty() ? "john" : "john " + residual,
        new ClientMention(clientId, "name", 1.0),
        residual,
        java.util.Set.of());
  }
}
