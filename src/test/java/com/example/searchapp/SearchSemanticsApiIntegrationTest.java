package com.example.searchapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.searchapp.shared.embedding.Embedder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

class SearchSemanticsApiIntegrationTest extends IntegrationTest {
  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final ObjectMapper JSON = new ObjectMapper();

  @Autowired private Embedder embedder;
  @Autowired private JdbcClient jdbc;
  @LocalServerPort private int port;

  @Test
  void returnsTheHighestScoringChunkAsTheDocumentPassage() throws Exception {
    String query = "best chunk regression";
    String content = "decoy passage strongest passage";
    String documentId =
        createDocument(createClient("passage-owner@example.com"), "Evidence", content);
    String bestPassage = "strongest passage";

    setOnlyChunkEmbedding(documentId, inverse(embedder.embed(query)));
    jdbc.sql(
            """
            INSERT INTO document_chunk
                (document_id, embedding_model, kind, ordinal, start_offset, end_offset, embedding)
            VALUES (:document_id, :embedding_model, 'body', 2, :start_offset, :end_offset, :embedding)
            """)
        .param("document_id", UUID.fromString(documentId))
        .param("embedding_model", embedder.modelId())
        .param("start_offset", content.indexOf(bestPassage))
        .param("end_offset", content.indexOf(bestPassage) + bestPassage.length())
        .param("embedding", new PGvector(embedder.embed(query)))
        .update();

    JsonNode results = search(query);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).path("type").asText()).isEqualTo("document");
    assertThat(results.get(0).path("match").path("passage").asText()).isEqualTo(bestPassage);
  }

  @Test
  void usesALabelChunkForSemanticAdmissionButABodyChunkForThePassage() throws Exception {
    String query = "label chunk isolation regression";
    String content = "unrelated body content";
    String documentId =
        createDocument(createClient("label-isolation-owner@example.com"), "Evidence", content);

    setOnlyChunkEmbedding(documentId, inverse(embedder.embed(query)));
    jdbc.sql(
            "UPDATE document_chunk SET embedding = :embedding"
                + " WHERE document_id = :document_id AND kind = 'label'")
        .param("embedding", new PGvector(embedder.embed(query)))
        .param("document_id", UUID.fromString(documentId))
        .update();

    JsonNode results = search(query);

    assertThat(results.toString())
        .contains(documentId)
        .contains("\"passage\":\"unrelated body content\"")
        .doesNotContain("\"passage\":\"\"");
  }

  @Test
  void keepsClientBeforeADocumentWithAHigherScore() throws Exception {
    String query = "lexical order signal";
    createClientWithSocialLink(
        "advisor@example.com", "https://www.linkedin.com/company/lexical-order-signal");
    String documentId =
        createDocument(createClient("document-owner@example.com"), "Evidence", "ranked passage");
    setOnlyChunkEmbedding(documentId, embedder.embed(query));

    JsonNode results = search(query);
    JsonNode matchedDocument = resultForDocument(results, documentId);

    assertThat(results.get(0).path("type").asText()).isEqualTo("client");
    assertThat(matchedDocument.path("type").asText()).isEqualTo("document");
    assertThat(matchedDocument.path("match").path("signals")).isNotEmpty();
  }

  @Test
  void promotesTheNamedClientsQualifiedDocumentForACompoundQuery() throws Exception {
    String query = "John utility bill";
    String johnId = createClient("John", "Doe", "john.doe@example.com");
    String maryId = createClient("Mary", "Henderson", "mary.henderson@example.com");
    String johnDocumentId = createDocument(johnId, "Utility Bill", "John's utility bill");
    String maryDocumentId = createDocument(maryId, "Utility Bill", "Mary's utility bill");
    float[] queryEmbedding = embedder.embed("utility bill");
    setOnlyChunkEmbedding(johnDocumentId, queryEmbedding);
    setOnlyChunkEmbedding(maryDocumentId, queryEmbedding);

    JsonNode results = search(query);

    assertThat(results.get(0).path("type").asText()).isEqualTo("document");
    assertThat(results.get(0).path("document").path("client_id").asText()).isEqualTo(johnId);
    assertThat(results.get(1).path("type").asText()).isEqualTo("client");
    assertThat(results.get(1).path("client").path("id").asText()).isEqualTo(johnId);
    assertThat(results.toString()).contains(johnDocumentId, maryDocumentId);
  }

  @Test
  void scopesAPossessiveClientNameEvenWhenACategoryIsAnotherClientsName() throws Exception {
    String minaId = createClient("Mina", "Ortiz", "mina.possessive@example.com");
    String billId = createClient("Bill", "Carter", "bill.possessive@example.com");
    String minaDocumentId = createDocument(minaId, "Utility Bill", "Mina's utility bill");
    String billDocumentId = createDocument(billId, "Utility Bill", "Bill's utility bill");
    float[] billEmbedding = embedder.embed("bill");
    setOnlyChunkEmbedding(minaDocumentId, billEmbedding);
    setOnlyChunkEmbedding(billDocumentId, billEmbedding);

    JsonNode results = search("Mina's bill");

    assertThat(results.get(0).path("type").asText()).isEqualTo("document");
    assertThat(results.get(0).path("document").path("client_id").asText()).isEqualTo(minaId);
    assertThat(results.get(1).path("type").asText()).isEqualTo("client");
    assertThat(results.get(1).path("client").path("id").asText()).isEqualTo(minaId);
  }

  @Test
  void excludesChunksProducedByAStaleEmbeddingModel() throws Exception {
    String query = "stale embedding model regression";
    String documentId =
        createDocument(createClient("stale-model-owner@example.com"), "Evidence", "stale passage");
    jdbc.sql(
            "UPDATE document_chunk SET embedding_model = :stale_model WHERE document_id = :document_id")
        .param("stale_model", "previous-model")
        .param("document_id", UUID.fromString(documentId))
        .update();

    JsonNode results = search(query);

    assertThat(results.toString()).doesNotContain(documentId);
  }

  @Test
  void excludesDocumentsWhoseBestChunkIsBelowTheSemanticFloor() throws Exception {
    String query = "below semantic floor regression";
    String documentId =
        createDocument(
            createClient("below-floor-owner@example.com"), "Evidence", "unrelated passage");
    setAllChunkEmbeddings(documentId, inverse(embedder.embed(query)));

    JsonNode results = search(query);

    assertThat(results.toString()).doesNotContain(documentId);
  }

  private JsonNode search(String query) throws Exception {
    var response =
        get(
            port,
            "/search?q="
                + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8),
            TEST_API_KEY);
    assertThat(response.statusCode()).isEqualTo(200);
    return JSON.readTree(response.body());
  }

  private String createClient(String email) throws Exception {
    return createClient("Search", "Owner", email);
  }

  private void createClientWithSocialLink(String email, String socialLink) throws Exception {
    var response =
        post(
            port,
            "/clients",
            TEST_API_KEY,
            "{\"first_name\":\"Search\",\"last_name\":\"Advisor\",\"email\":\"%s\",\"social_links\":[\"%s\"]}"
                .formatted(email, socialLink));
    assertThat(response.statusCode()).isEqualTo(201);
  }

  private String createClient(String firstName, String lastName, String email) throws Exception {
    var response =
        post(
            port,
            "/clients",
            TEST_API_KEY,
            "{\"first_name\":\"%s\",\"last_name\":\"%s\",\"email\":\"%s\"}"
                .formatted(firstName, lastName, email));
    assertThat(response.statusCode()).isEqualTo(201);
    return extractId(response.body());
  }

  private String createDocument(String clientId, String title, String content) throws Exception {
    var response =
        HTTP.send(
            HttpRequest.newBuilder(
                    java.net.URI.create(
                        "http://localhost:" + port + "/clients/" + clientId + "/documents"))
                .header("X-API-Key", TEST_API_KEY)
                .header("Content-Type", "application/json")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        "{\"title\":%s,\"content\":%s}"
                            .formatted(jsonString(title), jsonString(content))))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(201);
    return extractId(response.body());
  }

  /**
   * Sets the sole <em>body</em> chunk's embedding (every test document here has exactly one).
   * Scoped to {@code kind = 'body'} so it never touches the document's label chunk (ticket 15,
   * §5.3), which every created document also carries and which must never influence best-passage
   * selection.
   */
  private void setOnlyChunkEmbedding(String documentId, float[] embedding) {
    int updated =
        jdbc.sql(
                """
                UPDATE document_chunk SET embedding = :embedding
                WHERE document_id = :document_id AND kind = 'body'
                """)
            .param("embedding", new PGvector(embedding))
            .param("document_id", UUID.fromString(documentId))
            .update();
    assertThat(updated).isEqualTo(1);
  }

  private void setAllChunkEmbeddings(String documentId, float[] embedding) {
    int updated =
        jdbc.sql(
                "UPDATE document_chunk SET embedding = :embedding WHERE document_id = :document_id")
            .param("embedding", new PGvector(embedding))
            .param("document_id", UUID.fromString(documentId))
            .update();
    assertThat(updated).isGreaterThanOrEqualTo(2);
  }

  private static float[] inverse(float[] vector) {
    float[] inverse = new float[vector.length];
    for (int i = 0; i < vector.length; i++) {
      inverse[i] = -vector[i];
    }
    return inverse;
  }

  private static JsonNode resultForDocument(JsonNode results, String documentId) {
    for (JsonNode result : results) {
      if (documentId.equals(result.path("document").path("id").asText())) {
        return result;
      }
    }
    throw new AssertionError("search results did not contain document " + documentId);
  }

  private static String extractId(String json) {
    int start = json.indexOf("\"id\":\"") + 6;
    int end = json.indexOf('"', start);
    return json.substring(start, end);
  }

  private static String jsonString(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
