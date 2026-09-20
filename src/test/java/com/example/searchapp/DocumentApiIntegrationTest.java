package com.example.searchapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.searchapp.shared.embedding.Embedder;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

class DocumentApiIntegrationTest extends IntegrationTest {
  @LocalServerPort private int port;
  @Autowired private JdbcClient jdbcClient;

  @Test
  void createsAndFetchesADocumentWithEveryChunkCommittedAtomically() throws Exception {
    String clientId = createClient("Jane", "Doe", "jane.doe@example.com");

    var create =
        post(
            port,
            "/clients/" + clientId + "/documents",
            TEST_API_KEY,
            "{\"title\":\" 2024 Utility Bill \",\"content\":\" Account 123, due 15 June. \"}");

    assertThat(create.statusCode()).isEqualTo(201);
    assertThat(create.headers().firstValue("Location")).isPresent();
    String location = create.headers().firstValue("Location").orElseThrow();
    assertThat(location)
        .isEqualTo("/clients/" + clientId + "/documents/" + extractId(create.body()));
    assertThat(create.body())
        .contains("\"title\":\"2024 Utility Bill\"")
        .contains("\"content\":\"Account 123, due 15 June.\"")
        .contains("\"summary_status\":\"none\"")
        .contains("\"summary\":null")
        .contains("\"client_id\":\"" + clientId + "\"")
        .contains("\"document_type\":\"utility_bill\"")
        .contains("\"purposes\":[\"proof_of_address\"]")
        .contains("\"classification_source\":\"rule\"");

    String documentId = extractId(create.body());
    var fetched = get(port, URI.create(location).getPath(), TEST_API_KEY);
    assertThat(fetched.statusCode()).isEqualTo(200);
    assertThat(fetched.body()).isEqualTo(create.body());

    // One label chunk plus one body chunk (§5.2): every document carries a label chunk regardless
    // of how short its content is.
    Integer chunkCount =
        jdbcClient
            .sql("SELECT count(*) FROM document_chunk WHERE document_id = :id")
            .param("id", UUID.fromString(documentId))
            .query(Integer.class)
            .single();
    assertThat(chunkCount).isEqualTo(2);

    Integer labelChunkCount =
        jdbcClient
            .sql("SELECT count(*) FROM document_chunk WHERE document_id = :id AND kind = 'label'")
            .param("id", UUID.fromString(documentId))
            .query(Integer.class)
            .single();
    assertThat(labelChunkCount).isEqualTo(1);

    String embeddingModel =
        jdbcClient
            .sql("SELECT DISTINCT embedding_model FROM document_chunk WHERE document_id = :id")
            .param("id", UUID.fromString(documentId))
            .query(String.class)
            .single();
    assertThat(embeddingModel).isEqualTo(Embedder.MODEL_ID);
  }

  @Test
  void aLongDocumentIsChunkedAllTheWayToItsLastWord() throws Exception {
    String clientId = createClient("Long", "Document", "long.document@example.com");
    StringBuilder content = new StringBuilder();
    for (int i = 0; i < 999; i++) {
      content.append("filler ");
    }
    content.append("needle-at-word-one-thousand");

    var create =
        post(
            port,
            "/clients/" + clientId + "/documents",
            TEST_API_KEY,
            "{\"title\":\"Long\",\"content\":" + jsonString(content.toString()) + "}");

    assertThat(create.statusCode()).isEqualTo(201);
    String documentId = extractId(create.body());

    Integer chunksCoveringTheEnd =
        jdbcClient
            .sql(
                "SELECT count(*) FROM document_chunk WHERE document_id = :id AND end_offset = :len")
            .param("id", UUID.fromString(documentId))
            .param("len", content.length())
            .query(Integer.class)
            .single();
    assertThat(chunksCoveringTheEnd).isEqualTo(1);
  }

  @Test
  void rejectsInvalidBodiesAndTreatsUnknownOrMalformedIdsAsNotFound() throws Exception {
    String clientId = createClient("Val", "Idator", "val.idator@example.com");

    var blank =
        post(
            port,
            "/clients/" + clientId + "/documents",
            TEST_API_KEY,
            "{\"title\":\"\",\"content\":\"\"}");
    assertThat(blank.statusCode()).isEqualTo(400);
    assertThat(blank.body()).contains("errors").contains("title").contains("content");

    var unknownClient =
        post(
            port,
            "/clients/00000000-0000-0000-0000-000000000000/documents",
            TEST_API_KEY,
            "{\"title\":\"T\",\"content\":\"C\"}");
    assertThat(unknownClient.statusCode()).isEqualTo(404);

    var malformedClient =
        post(
            port,
            "/clients/not-a-uuid/documents",
            TEST_API_KEY,
            "{\"title\":\"T\",\"content\":\"C\"}");
    assertThat(malformedClient.statusCode()).isEqualTo(404);

    var create =
        post(
            port,
            "/clients/" + clientId + "/documents",
            TEST_API_KEY,
            "{\"title\":\"T\",\"content\":\"C\"}");
    String documentId = extractId(create.body());

    var unknownDocument =
        get(
            port,
            "/clients/" + clientId + "/documents/00000000-0000-0000-0000-000000000000",
            TEST_API_KEY);
    assertThat(unknownDocument.statusCode()).isEqualTo(404);

    var malformedDocument =
        get(port, "/clients/" + clientId + "/documents/not-a-uuid", TEST_API_KEY);
    assertThat(malformedDocument.statusCode()).isEqualTo(404);

    // a document created under a different client is not found through this one
    String otherClientId = createClient("Other", "Client", "other.client@example.com");
    var wrongClient =
        get(port, "/clients/" + otherClientId + "/documents/" + documentId, TEST_API_KEY);
    assertThat(wrongClient.statusCode()).isEqualTo(404);
  }

  private String createClient(String firstName, String lastName, String email) throws Exception {
    var response =
        post(
            port,
            "/clients",
            TEST_API_KEY,
            "{\"first_name\":\"%s\",\"last_name\":\"%s\",\"email\":\"%s\"}"
                .formatted(firstName, lastName, email));
    return extractId(response.body());
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
