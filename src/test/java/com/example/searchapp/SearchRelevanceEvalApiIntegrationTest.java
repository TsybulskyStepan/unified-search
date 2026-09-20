package com.example.searchapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.searchapp.eval.EvalCorpusLoader;
import com.example.searchapp.eval.EvalQueries;
import com.example.searchapp.onboarding.seed.DemoCorpus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

  private record Result(String type, String key) {
    boolean isClient() {
      return type.equals("client");
    }
  }

  private record Outcome(
      EvalQueries.EvalQuery query,
      List<Result> results,
      List<EvalQueries.Item> expectedItems,
      List<String> expectedKeys,
      List<Integer> expectedRanks,
      int n) {
    int recallHits() {
      return (int) expectedRanks.stream().filter(rank -> rank > 0 && rank <= n).count();
    }

    double reciprocalRank() {
      return expectedRanks.stream()
          .filter(rank -> rank > 0)
          .mapToInt(Integer::intValue)
          .min()
          .stream()
          .mapToDouble(rank -> 1.0 / rank)
          .findFirst()
          .orElse(0.0);
    }
  }

  @Test
  void everyQueryMeetsItsExpectationShapeAndNoClientOutranksAnExpectedDocument() throws Exception {
    DemoCorpus corpus = EvalCorpusLoader.corpus();
    EvalQueries queries = EvalCorpusLoader.queries();
    Map<String, String> clientNames =
        corpus.clients().stream()
            .collect(
                Collectors.toMap(
                    DemoCorpus.DemoClient::email,
                    client -> client.firstName() + " " + client.lastName()));

    // Measure and log every query before asserting, so one failure cannot hide the rest.
    List<Outcome> outcomes = new ArrayList<>();
    for (EvalQueries.EvalQuery query : queries.queries()) {
      List<EvalQueries.Item> items = queries.resolve(query, corpus);
      List<String> expectedKeys = items.stream().map(item -> keyOf(item, clientNames)).toList();
      List<Result> results = results(query.query());
      List<Integer> ranks = expectedKeys.stream().map(key -> rankOf(results, key)).toList();
      int n =
          switch (query.shape()) {
            case FIRST -> 1;
            case ALL_WITHIN, COMPOUND -> expectedKeys.size();
            case NONE -> 0;
          };
      outcomes.add(new Outcome(query, results, items, expectedKeys, ranks, n));
    }

    // A total miss has reciprocal rank 0, so it lowers MRR instead of dividing by zero.
    List<Outcome> ranked =
        outcomes.stream().filter(o -> o.query().shape() != EvalQueries.Shape.NONE).toList();
    for (Outcome outcome : outcomes) {
      log.info(
          "eval query='{}' shape={} n={} recall@n={}/{} rr={} results={}",
          outcome.query().query(),
          outcome.query().shape(),
          outcome.n(),
          outcome.recallHits(),
          outcome.expectedKeys().size(),
          outcome.reciprocalRank(),
          outcome.results().size());
    }
    double mrr = ranked.stream().mapToDouble(Outcome::reciprocalRank).average().orElseThrow();
    double meanRecall =
        ranked.stream()
            .mapToDouble(o -> (double) o.recallHits() / o.expectedKeys().size())
            .average()
            .orElseThrow();
    log.info("eval summary queries={} MRR={} meanRecall@n={}", outcomes.size(), mrr, meanRecall);

    SoftAssertions softly = new SoftAssertions();
    for (Outcome outcome : outcomes) {
      assertShape(softly, outcome);
      assertNoClientAboveExpectedDocument(softly, outcome);
    }
    softly.assertAll();
  }

  private static int rankOf(List<Result> results, String key) {
    for (int index = 0; index < results.size(); index++) {
      if (results.get(index).key().equals(key)) {
        return index + 1;
      }
    }
    return 0;
  }

  private static void assertShape(SoftAssertions softly, Outcome outcome) {
    String name = outcome.query().shape() + " '" + outcome.query().query() + "'";
    List<Result> results = outcome.results();
    switch (outcome.query().shape()) {
      case FIRST ->
          softly
              .assertThat(results.isEmpty() ? null : results.get(0).key())
              .as("%s: position 1", name)
              .isEqualTo(outcome.expectedKeys().get(0));
      case ALL_WITHIN ->
          softly
              .assertThat(outcome.expectedRanks())
              .as(
                  "%s: every expected item inside the first %d positions (rank 0 = absent)",
                  name, outcome.n())
              .allSatisfy(rank -> assertThat(rank).isBetween(1, outcome.n()));
      case COMPOUND -> {
        // §6.5 tier 1: the client's own documents may sit between the expected document and it.
        String documentKey = outcome.expectedKeys().get(0);
        String clientKey = outcome.expectedKeys().get(1);
        softly
            .assertThat(results.isEmpty() ? null : results.get(0).key())
            .as("%s: expected document first", name)
            .isEqualTo(documentKey);
        int clientIndex = rankOf(results, clientKey) - 1;
        softly.assertThat(clientIndex).as("%s: expected client present", name).isPositive();
        String ownDocuments = "document::" + clientKey.substring("client::".length()) + "::";
        for (int index = 1; index < clientIndex; index++) {
          softly
              .assertThat(results.get(index).key())
              .as("%s: only the client's own documents may precede the client", name)
              .startsWith(ownDocuments);
        }
        softly
            .assertThat(results.subList(Math.max(clientIndex + 1, 0), results.size()))
            .as("%s: none of the client's own documents follow the client", name)
            .noneMatch(result -> result.key().startsWith(ownDocuments));
      }
      case NONE -> softly.assertThat(results).as("%s: empty result", name).isEmpty();
    }
  }

  private static void assertNoClientAboveExpectedDocument(SoftAssertions softly, Outcome outcome) {
    int lastExpectedDocument = 0;
    for (int index = 0; index < outcome.expectedItems().size(); index++) {
      if (outcome.expectedItems().get(index).isDocument()) {
        lastExpectedDocument = Math.max(lastExpectedDocument, outcome.expectedRanks().get(index));
      }
    }
    for (int index = 0; index < Math.min(lastExpectedDocument, outcome.results().size()); index++) {
      softly
          .assertThat(outcome.results().get(index).isClient())
          .as(
              "'%s': no client above an expected document (client at position %d, last expected"
                  + " document at %d)",
              outcome.query().query(), index + 1, lastExpectedDocument)
          .isFalse();
    }
  }

  private static String keyOf(EvalQueries.Item item, Map<String, String> clientNames) {
    String name = clientNames.get(item.clientEmail());
    return item.isDocument() ? "document::" + name + "::" + item.title() : "client::" + name;
  }

  private List<Result> results(String query) throws Exception {
    List<Result> results = new ArrayList<>();
    for (JsonNode result : allResults(query)) {
      if (result.path("type").asText().equals("client")) {
        JsonNode client = result.path("client");
        results.add(
            new Result(
                "client",
                "client::"
                    + client.path("first_name").asText()
                    + " "
                    + client.path("last_name").asText()));
      } else {
        JsonNode document = result.path("document");
        results.add(
            new Result(
                "document",
                "document::"
                    + document.path("client_name").asText()
                    + "::"
                    + document.path("title").asText()));
      }
    }
    return results;
  }

  @Test
  void lexicalFloorAdmitsAMisspelledNameAndRejectsAShortNearMiss() throws Exception {
    // §6.2 anchors: Hendersen 0.70 against Mary Henderson, joe 0.50 against John Doe.
    assertClientFirst(search("Hendersen"), "Mary", "Henderson");
    assertThat(search("joe")).noneMatch(result -> result.path("type").asText().equals("client"));
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
    String query = "John Doe utility bill";
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

  @Test
  void pagesACategoryQueryAsSlicesOfOneStableOrdering() throws Exception {
    String query = "proof of address";
    JsonNode fullResults = search(query);

    assertThat(fullResults.size()).isGreaterThan(2);
    for (int offset = 0; offset < fullResults.size(); offset++) {
      assertThat(search(query, 1, offset)).containsExactly(fullResults.get(offset));
    }
    for (int offset = 0; offset < fullResults.size(); offset += 2) {
      int end = Math.min(offset + 2, fullResults.size());
      List<JsonNode> expected = new ArrayList<>();
      fullResults.forEach(expected::add);
      assertThat(search(query, 2, offset)).containsExactlyElementsOf(expected.subList(offset, end));
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

  private List<JsonNode> allResults(String query) throws Exception {
    List<JsonNode> results = new ArrayList<>();
    int offset = 0;
    while (true) {
      JsonNode page = search(query, 50, offset);
      page.forEach(results::add);
      if (page.size() < 50) {
        return results;
      }
      offset += page.size();
    }
  }

  private JsonNode search(String query, int limit, int offset) throws Exception {
    var response =
        get(
            port,
            "/search?q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&limit="
                + limit
                + "&offset="
                + offset,
            TEST_API_KEY);
    assertThat(response.statusCode()).isEqualTo(200);
    return JSON.readTree(response.body());
  }
}
