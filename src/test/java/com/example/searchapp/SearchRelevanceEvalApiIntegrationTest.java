package com.example.searchapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.searchapp.eval.EvalCorpusLoader;
import com.example.searchapp.eval.EvalQueries;
import com.example.searchapp.onboarding.seed.DemoCorpus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.assertj.core.api.SoftAssertions;
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
  void everyQuerysLabelledDocumentSetOccupiesTheTopRanksAndLogsMrr() throws Exception {
    DemoCorpus corpus = EvalCorpusLoader.corpus();
    Map<String, String> clientNames =
        corpus.clients().stream()
            .collect(
                Collectors.toMap(
                    DemoCorpus.DemoClient::email,
                    client -> client.firstName() + " " + client.lastName()));

    Map<String, List<EvalQueries.PositivePair>> pairsByQuery =
        EvalCorpusLoader.queries().positives().stream()
            .collect(
                Collectors.groupingBy(
                    EvalQueries.PositivePair::query, LinkedHashMap::new, Collectors.toList()));

    // Recall@N with a hard purity constraint: for every query, label every genuinely relevant
    // document, then require that the labelled set occupies exactly ranks 1..N (N = its size),
    // with nothing unlabelled interleaved. Single-answer queries become rank-1-or-fail. This is
    // compatible with the same artifact type existing for more than one client (a duplicate just
    // grows N) instead of assuming one "expected document" per query.
    //
    // Every query is measured and logged before any assertion runs, so the MRR and per-query
    // outcome are always recorded — the same requirement as the identifier-probe query: the
    // outcome is recorded whether or not it passes, not only when every query already passes.
    record QueryResult(
        String query, List<String> topRanks, Set<String> labelled, int firstHitRank) {}

    List<QueryResult> queryResults = new ArrayList<>();
    for (Map.Entry<String, List<EvalQueries.PositivePair>> entry : pairsByQuery.entrySet()) {
      Set<String> labelledDocuments =
          entry.getValue().stream()
              .map(pair -> labelKey(clientNames.get(pair.clientEmail()), pair.documentTitle()))
              .collect(Collectors.toCollection(LinkedHashSet::new));

      JsonNode results = search(entry.getKey());
      List<String> topRanks = new ArrayList<>();
      int firstHitRank = 0;
      for (int index = 0; index < results.size(); index++) {
        JsonNode result = results.get(index);
        String key =
            labelKey(
                result.path("document").path("client_name").asText(),
                result.path("document").path("title").asText());
        if (firstHitRank == 0
            && result.path("type").asText().equals("document")
            && labelledDocuments.contains(key)) {
          firstHitRank = index + 1;
        }
        if (index < labelledDocuments.size()) {
          topRanks.add(key);
        }
      }
      queryResults.add(new QueryResult(entry.getKey(), topRanks, labelledDocuments, firstHitRank));
    }

    // A miss (rank 0, i.e. the labelled document never appears at all) contributes 0 to MRR
    // rather than dividing by zero — a total miss must lower MRR, not raise it to Infinity.
    List<Integer> firstHitRanks = queryResults.stream().map(QueryResult::firstHitRank).toList();
    double mrr =
        firstHitRanks.stream()
            .mapToDouble(rank -> rank > 0 ? 1.0 / rank : 0.0)
            .average()
            .orElseThrow();
    log.info("Search relevance evaluation firstHitRanks={} MRR={}", firstHitRanks, mrr);

    SoftAssertions softly = new SoftAssertions();
    for (QueryResult result : queryResults) {
      softly
          .assertThat(result.topRanks())
          .as(
              "top %d results for query length %s",
              result.labelled().size(), result.query().length())
          .containsExactlyInAnyOrderElementsOf(result.labelled());
    }
    softly.assertAll();
  }

  private static String labelKey(String clientName, String documentTitle) {
    return clientName + "::" + documentTitle;
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
