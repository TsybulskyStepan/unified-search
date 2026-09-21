package com.example.searchapp.onboarding.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.searchapp.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * HTTP-level acceptance of ticket 15 (§3.3, §4.2): classification on {@code POST}, its validation,
 * and the invariant that a row's staleness never affects whether it can be read.
 */
class DocumentClassificationApiIntegrationTest extends IntegrationTest {
  @LocalServerPort private int port;
  @Autowired private JdbcClient jdbcClient;

  @Test
  void anUnrecognisedDocumentIsStoredAsUnknownAndStillSearchable() throws Exception {
    String clientId = createClient("unknown.type@example.com");

    var create =
        post(
            port,
            "/clients/" + clientId + "/documents",
            TEST_API_KEY,
            "{\"title\":\"Holiday Itinerary\",\"content\":\"Nothing matches a pattern here.\"}");

    assertThat(create.statusCode()).isEqualTo(201);
    assertThat(create.body())
        .contains("\"document_type\":\"unknown\"")
        .contains("\"purposes\":[]")
        .contains("\"classification_source\":\"unknown\"");

    String documentId = extractId(create.body());
    var fetched = get(port, "/clients/" + clientId + "/documents/" + documentId, TEST_API_KEY);
    assertThat(fetched.statusCode()).isEqualTo(200);
  }

  @Test
  void aTiedTitleClassifiesAsUnknownRatherThanAnArbitraryWinner() throws Exception {
    String clientId = createClient("tied.type@example.com");

    var create =
        post(
            port,
            "/clients/" + clientId + "/documents",
            TEST_API_KEY,
            "{\"title\":\"Council Tax Utility Bill\",\"content\":\"No content patterns here.\"}");

    assertThat(create.statusCode()).isEqualTo(201);
    assertThat(create.body()).contains("\"document_type\":\"unknown\"");
  }

  @Test
  void aRequestedTypeIsUsedAsIsAndRecordedAsTheSource() throws Exception {
    String clientId = createClient("requested.type@example.com");

    var create =
        post(
            port,
            "/clients/" + clientId + "/documents",
            TEST_API_KEY,
            "{\"title\":\"Some Notes\",\"content\":\"Nothing that would rule-classify.\","
                + "\"document_type\":\"bank_statement\",\"purposes\":[\"source_of_funds\"]}");

    assertThat(create.statusCode()).isEqualTo(201);
    assertThat(create.body())
        .contains("\"document_type\":\"bank_statement\"")
        .contains("\"purposes\":[\"source_of_funds\"]")
        .contains("\"classification_source\":\"request\"");
  }

  @Test
  void aRequestedTypeWithNoPurposesFallsBackToTheTypesDefaults() throws Exception {
    String clientId = createClient("requested.defaults@example.com");

    var create =
        post(
            port,
            "/clients/" + clientId + "/documents",
            TEST_API_KEY,
            "{\"title\":\"Some Notes\",\"content\":\"Nothing that would rule-classify.\","
                + "\"document_type\":\"passport\"}");

    assertThat(create.statusCode()).isEqualTo(201);
    assertThat(create.body())
        .contains("\"document_type\":\"passport\"")
        .contains("\"purposes\":[\"proof_of_identity\"]")
        .contains("\"classification_source\":\"request\"");
  }

  @Test
  void rejectsAnUnrecognisedRequestedDocumentType() throws Exception {
    String clientId = createClient("invalid.type@example.com");

    var create =
        post(
            port,
            "/clients/" + clientId + "/documents",
            TEST_API_KEY,
            "{\"title\":\"T\",\"content\":\"C\",\"document_type\":\"not_a_real_type\"}");

    assertThat(create.statusCode()).isEqualTo(400);
    assertThat(create.body()).contains("document_type");
  }

  @Test
  void rejectsPurposesGivenWithoutADocumentType() throws Exception {
    String clientId = createClient("purposes.without.type@example.com");

    var create =
        post(
            port,
            "/clients/" + clientId + "/documents",
            TEST_API_KEY,
            "{\"title\":\"T\",\"content\":\"C\",\"purposes\":[\"proof_of_address\"]}");

    assertThat(create.statusCode()).isEqualTo(400);
    assertThat(create.body()).contains("purposes");
  }

  @Test
  void rejectsAnUnrecognisedRequestedPurpose() throws Exception {
    String clientId = createClient("invalid.purpose@example.com");

    var create =
        post(
            port,
            "/clients/" + clientId + "/documents",
            TEST_API_KEY,
            "{\"title\":\"T\",\"content\":\"C\",\"document_type\":\"utility_bill\","
                + "\"purposes\":[\"not_a_real_purpose\"]}");

    assertThat(create.statusCode()).isEqualTo(400);
    assertThat(create.body()).contains("purposes");
  }

  /**
   * A row a taxonomy version bump left behind is fully readable under its old labels — the "briefly
   * stale, never absent" contract (§2.2, §3.4) — because nothing on the read path checks {@code
   * taxonomy_version}. This session's own {@code Reclassifier} run happened once, at this shared
   * context's startup, well before this row existed, so it stays stale for the rest of this class.
   */
  @Test
  void aRowBelowTheCurrentTaxonomyVersionStaysFullyReadable() throws Exception {
    String clientId = createClient("stale.row@example.com");
    UUID documentId = UUID.randomUUID();
    jdbcClient
        .sql(
            """
            INSERT INTO document
                (id, client_id, title, content, summary_status,
                 document_type, purposes, classification_source, taxonomy_version, label_text)
            VALUES (:id, :client_id, 'Old Label Document', 'Some content.', 'none',
                    'utility_bill', ARRAY['proof_of_address'], 'rule', 0, 'utility bill proof of address')
            """)
        .param("id", documentId)
        .param("client_id", UUID.fromString(clientId))
        .update();

    var fetched = get(port, "/clients/" + clientId + "/documents/" + documentId, TEST_API_KEY);

    assertThat(fetched.statusCode()).isEqualTo(200);
    assertThat(fetched.body())
        .contains("\"document_type\":\"utility_bill\"")
        .contains("\"purposes\":[\"proof_of_address\"]")
        .contains("\"classification_source\":\"rule\"");
  }

  private String createClient(String email) throws Exception {
    var response =
        post(
            port,
            "/clients",
            TEST_API_KEY,
            "{\"first_name\":\"Class\",\"last_name\":\"Ification\",\"email\":\"%s\"}"
                .formatted(email));
    return extractId(response.body());
  }

  private static String extractId(String json) {
    int start = json.indexOf("\"id\":\"") + 6;
    int end = json.indexOf('"', start);
    return json.substring(start, end);
  }
}
