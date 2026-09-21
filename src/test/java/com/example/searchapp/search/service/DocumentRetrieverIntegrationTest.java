package com.example.searchapp.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.searchapp.IntegrationTest;
import com.example.searchapp.search.planner.QueryPlan;
import com.example.searchapp.search.planner.QueryPlanner;
import com.example.searchapp.search.repository.model.DocumentMatch;
import com.example.searchapp.search.repository.model.HydratedDocument;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

class DocumentRetrieverIntegrationTest extends IntegrationTest {
  @LocalServerPort private int port;
  @Autowired private DocumentRetriever retriever;
  @Autowired private QueryPlanner planner;

  @Test
  void doesNothingWhenThePlanHasNoResidual() {
    QueryPlan plan = new QueryPlan("john", List.of(), "", Set.of(), Set.of());

    RetrievalResult result = retriever.retrieve(plan);

    assertThat(result.matches()).isEmpty();
    assertThat(result.ran()).isFalse();
    assertThat(result.queryVector()).isEmpty();
  }

  @Test
  void doesNothingWhenTheResidualIsOnlyStopWords() {
    RetrievalResult result = retriever.retrieve(planner.plan("the and", List.of()));

    assertThat(result.matches()).isEmpty();
    assertThat(result.ran()).isFalse();
    assertThat(result.queryVector()).isEmpty();
  }

  @Test
  void retrievesAndFusesMatchesAndKeepsTheQueryVector() throws Exception {
    UUID documentId = createClientWithDocument("retrieves");

    RetrievalResult result =
        retriever.retrieve(planner.plan("occupancy registered address", List.of()));

    assertThat(result.ran()).isTrue();
    assertThat(result.queryVector()).isPresent();
    assertThat(result.matches()).extracting(DocumentMatch::documentId).contains(documentId);
    assertThat(result.measurements().lexicalHits()).isPositive();
  }

  @Test
  void hydratesTheBestPassageWithoutTheCallerHoldingTheVector() throws Exception {
    UUID documentId = createClientWithDocument("hydrates");
    RetrievalResult result =
        retriever.retrieve(planner.plan("occupancy registered address", List.of()));

    Map<UUID, HydratedDocument> hydrated = retriever.hydrate(result.matches(), result);

    assertThat(hydrated).containsKey(documentId);
    assertThat(hydrated.get(documentId).passage()).isNotBlank();
    assertThat(hydrated.get(documentId).document().title()).isEqualTo("Utility bill hydrates");
  }

  @Test
  void hydratingNoMatchesNeedsNoVector() {
    RetrievalResult skipped =
        retriever.retrieve(new QueryPlan("john", List.of(), "", Set.of(), Set.of()));

    assertThat(retriever.hydrate(List.of(), skipped)).isEmpty();
  }

  private UUID createClientWithDocument(String tag) throws Exception {
    var client =
        post(
            port,
            "/clients",
            TEST_API_KEY,
            "{\"first_name\":\"Retriever\",\"last_name\":\"%s\",\"email\":\"retriever.%s@example.test\"}"
                .formatted(tag, tag));
    assertThat(client.statusCode()).isEqualTo(201);
    String clientId = extractId(client.body());
    var document =
        post(
            port,
            "/clients/" + clientId + "/documents",
            TEST_API_KEY,
            ("{\"title\":\"Utility bill %s\",\"content\":\"Registered supply address confirms"
                    + " occupancy for the period shown above.\"}")
                .formatted(tag));
    assertThat(document.statusCode()).isEqualTo(201);
    return UUID.fromString(extractId(document.body()));
  }

  private static String extractId(String json) {
    int start = json.indexOf("\"id\":\"") + 6;
    return json.substring(start, json.indexOf('"', start));
  }
}
