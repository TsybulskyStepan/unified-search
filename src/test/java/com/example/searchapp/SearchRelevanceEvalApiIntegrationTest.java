package com.example.searchapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.searchapp.eval.EvalCorpusLoader;
import com.example.searchapp.eval.EvalQueries;
import com.example.searchapp.onboarding.seed.DemoCorpus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "app.seed.enabled=true")
class SearchRelevanceEvalApiIntegrationTest extends IntegrationTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Logger log =
      LoggerFactory.getLogger(SearchRelevanceEvalApiIntegrationTest.class);

  @LocalServerPort private int port;

  @Test
  void genericCategoryQueryDoesNotPromoteClientWhoseFirstNameIsACategoryTerm() throws Exception {
    JsonNode results = search("utility bill");

    assertThat(results).isNotEmpty();
    assertThat(results)
        .allSatisfy(result -> assertThat(result.path("type").asText()).isEqualTo("document"));
  }

  @Test
  void returnsEveryExpectedSemanticDocumentWithinTheTopThreeAndLogsMrr() throws Exception {
    DemoCorpus corpus = EvalCorpusLoader.corpus();
    Map<String, String> clientNames =
        corpus.clients().stream()
            .collect(
                Collectors.toMap(
                    DemoCorpus.DemoClient::email,
                    client -> client.firstName() + " " + client.lastName()));

    List<Integer> ranks = new ArrayList<>();
    for (EvalQueries.PositivePair pair : EvalCorpusLoader.queries().positives()) {
      int rank =
          rankOfExpectedDocument(search(pair.query()), pair, clientNames.get(pair.clientEmail()));
      ranks.add(rank);
    }

    double mrr = ranks.stream().mapToDouble(rank -> 1.0 / rank).average().orElseThrow();
    log.info("Search relevance evaluation ranks={} MRR={}", ranks, mrr);
    assertThat(ranks).allSatisfy(rank -> assertThat(rank).isBetween(1, 3));
  }

  @Test
  void excludesDocumentsForEveryUnrelatedQuery() throws Exception {
    for (String query : EvalCorpusLoader.queries().negatives()) {
      assertThat(search(query))
          .as("query length %s", query.length())
          .noneMatch(result -> result.path("type").asText().equals("document"));
    }
  }

  @Test
  void excludesClientsForTheDocumentQueriesThatGuardTheLexicalFloor() throws Exception {
    for (String query : List.of("address proof", "proof of identity", "source of funds")) {
      assertThat(search(query))
          .as("query length %s", query.length())
          .noneMatch(result -> result.path("type").asText().equals("client"));
    }
  }

  @Test
  void keepsClientIdentifierQueriesClientFirst() throws Exception {
    assertClientFirst(search("NevisWealth"), "John", "Doe");
    assertClientFirst(search("Hendersen"), "Mary", "Henderson");
  }

  @Test
  void scopesMisspelledCompoundQueriesAndKeepsOtherMatchingArtifacts() throws Exception {
    JsonNode results = search("Hendersen council tax");

    assertDocumentFirst(results, "Mary Henderson", "Council Tax Bill 2024/25");
    assertNamedClientDocumentsPrecedeClient(results, "Mary", "Henderson");
    assertThat(results)
        .anyMatch(
            result ->
                result.path("type").asText().equals("document")
                    && result.path("document").path("client_name").asText().equals("Grace Kim")
                    && result
                        .path("document")
                        .path("title")
                        .asText()
                        .equals("Council Tax Bill 2024/25"));
  }

  @Test
  void ordersCompoundQueryBeforePaging() throws Exception {
    String query = "John utility bill";
    JsonNode fullResults = search(query);

    assertDocumentFirst(fullResults, "John Doe", "2024 Utility Bill");
    assertNamedClientDocumentsPrecedeClient(fullResults, "John", "Doe");
    assertThat(fullResults)
        .anyMatch(
            result ->
                result.path("type").asText().equals("document")
                    && result.path("document").path("client_name").asText().equals("Samuel Okafor")
                    && result.path("document").path("title").asText().equals("2024 Utility Bill"));

    for (int offset = 0; offset < fullResults.size(); offset++) {
      JsonNode page = search(query, 1, offset);
      assertThat(page).containsExactly(fullResults.get(offset));
    }
  }

  private static int rankOfExpectedDocument(
      JsonNode results, EvalQueries.PositivePair pair, String clientName) {
    for (int index = 0; index < results.size(); index++) {
      JsonNode result = results.get(index);
      if (result.path("type").asText().equals("document")
          && result.path("document").path("title").asText().equals(pair.documentTitle())
          && result.path("document").path("client_name").asText().equals(clientName)) {
        return index + 1;
      }
    }
    return 0;
  }

  private static void assertDocumentFirst(JsonNode results, String clientName, String title) {
    JsonNode first = results.get(0);
    assertThat(first.path("type").asText()).isEqualTo("document");
    assertThat(first.path("document").path("client_name").asText()).isEqualTo(clientName);
    assertThat(first.path("document").path("title").asText()).isEqualTo(title);
  }

  private static void assertClientFirst(JsonNode results, String firstName, String lastName) {
    JsonNode first = results.get(0);
    assertThat(first.path("type").asText()).isEqualTo("client");
    assertThat(first.path("client").path("first_name").asText()).isEqualTo(firstName);
    assertThat(first.path("client").path("last_name").asText()).isEqualTo(lastName);
  }

  private static void assertNamedClientDocumentsPrecedeClient(
      JsonNode results, String firstName, String lastName) {
    String clientName = firstName + " " + lastName;
    int clientIndex = -1;
    for (int index = 0; index < results.size(); index++) {
      JsonNode result = results.get(index);
      if (result.path("type").asText().equals("client")
          && result.path("client").path("first_name").asText().equals(firstName)
          && result.path("client").path("last_name").asText().equals(lastName)) {
        clientIndex = index;
        break;
      }
    }

    assertThat(clientIndex).isPositive();
    for (int index = 0; index < clientIndex; index++) {
      JsonNode result = results.get(index);
      assertThat(result.path("type").asText()).isEqualTo("document");
      assertThat(result.path("document").path("client_name").asText()).isEqualTo(clientName);
    }
  }

  private JsonNode search(String query) throws Exception {
    return search(query, 50, 0);
  }

  private JsonNode search(String query, int limit, int offset) throws Exception {
    var response =
        get(
            port,
            "/search?q=" + query.replace(" ", "%20") + "&limit=" + limit + "&offset=" + offset,
            TEST_API_KEY);
    assertThat(response.statusCode()).isEqualTo(200);
    return JSON.readTree(response.body());
  }
}
