package com.example.searchapp.search.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.searchapp.IntegrationTest;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

class SearchDocumentApiIntegrationTest extends IntegrationTest {
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  @LocalServerPort private int port;

  @Test
  void findsAnAddressProofArtifactWithPassageAndClientAttribution() throws Exception {
    String clientId = createClient("Jane", "Doe", "jane.doe@example.com");
    createDocument(
        clientId,
        "2024 Utility Bill",
        """
        Account number 8827-4491-002 held with Thameswater Utilities Ltd for the billing period
        01/03/2024 to 31/05/2024. Closing balance of GBP 184.62 including VAT at 20%. Previous
        meter reading 48213 kWh, current reading 49876 kWh, estimated usage 1,663 kWh at GBP
        0.2841 per unit plus a standing charge of GBP 0.4521 per day, totalling GBP 221.90 before
        the direct debit credit of GBP 150.00 applied on 12/04/2024, reference DD-2024-0417-JD.
        Payment is due by 15/06/2024 to sort code 40-12-09, account 00281774, or online at
        pay.thameswater-utilities.example using customer reference TWU-JD-5521. Registered supply
        address: Flat 4, 22 Willowmead Crescent, Reading, RG1 4PQ, United Kingdom. Contact the
        helpline on 0345 900 0800 for meter disputes or tariff queries. Retain this statement; it
        is issued in the account holder's name and confirms occupancy at the registered address for
        the period shown above.
        """);

    var response = get(port, "/search?q=address%20proof", TEST_API_KEY);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body())
        .contains("\"type\":\"document\"")
        .contains("\"title\":\"2024 Utility Bill\"")
        .contains("\"client_name\":\"Jane Doe\"")
        .contains("\"passage\":\"account holder's name and confirms occupancy")
        .doesNotContain("\"type\":\"client\"")
        .doesNotContain("\"content\"");
  }

  @Test
  void admitsAPurposeTaggedDocumentWhoseTextDoesNotUseTheQueryWords() throws Exception {
    String clientId = createClient("Opaque", "Record", "opaque.record@example.com");
    createDocument(
        clientId,
        "Monthly supplier record",
        "Reference 92381. The account is settled and the enclosed figures are final.",
        "utility_bill");

    var response = get(port, "/search?q=proof%20of%20address", TEST_API_KEY);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body())
        .contains("\"title\":\"Monthly supplier record\"")
        .contains("\"signals\":[\"label\"")
        .contains("\"labels\":[\"purpose:proof_of_address\"");
  }

  @Test
  void admitsStemmedLexicalMatchesInTitleLabelsAndContent() throws Exception {
    String clientId = createClient("Lexical", "Signals", "lexical.signals@example.com");
    createDocument(clientId, "Verification archive", "Opaque reference 102.");
    createDocument(clientId, "Supplier archive", "Opaque reference 103.", "utility_bill");
    createDocument(clientId, "Supplier archive", "The declaration is recorded here.");

    assertThat(get(port, "/search?q=verifications", TEST_API_KEY).body())
        .contains("\"title\":\"Verification archive\"")
        .contains("\"lexical\"");
    assertThat(get(port, "/search?q=addresses", TEST_API_KEY).body())
        .contains("\"title\":\"Supplier archive\"")
        .contains("\"lexical\"");
    assertThat(get(port, "/search?q=declarations", TEST_API_KEY).body())
        .contains("The declaration is recorded here")
        .contains("\"lexical\"");
  }

  @Test
  void matchesTheWordBeingTypedAsAPrefixOfTheLastQueryTerm() throws Exception {
    String clientId = createClient("Tolliver", "Vance", "tolliver.vance@example.com");
    createDocument(clientId, "Tenancy Agreement 2025", "Rent of GBP 950 per month for the flat.");

    var named = get(port, "/search?q=vance%20agreemen", TEST_API_KEY).body();
    assertThat(named).startsWith("[{\"type\":\"document\"");
    assertThat(named).contains("\"title\":\"Tenancy Agreement 2025\"").contains("\"lexical\"");

    var unnamed = get(port, "/search?q=tenancy%20agreemen", TEST_API_KEY).body();
    assertThat(unnamed).contains("\"title\":\"Tenancy Agreement 2025\"").contains("\"lexical\"");

    assertThat(get(port, "/search?q=vance%20ag", TEST_API_KEY).body())
        .as("a one- or two-letter tail is too short to match as a prefix")
        .doesNotContain("\"lexical\"");
  }

  @Test
  void skipsDocumentRetrievalWhenTheResidualContainsOnlyStopWords() throws Exception {
    String clientId = createClient("Stop", "Words", "stop.words@example.com");
    createDocument(clientId, "Supplier archive", "The declaration is recorded here.");

    var response = get(port, "/search?q=the%20and", TEST_API_KEY);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).doesNotContain("\"type\":\"document\"");
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

  private void createDocument(String clientId, String title, String content) throws Exception {
    createDocument(clientId, title, content, null);
  }

  private void createDocument(String clientId, String title, String content, String documentType)
      throws Exception {
    String documentTypeField =
        documentType == null ? "" : ",\"document_type\":\"" + documentType + "\"";
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
                            .replace("}", documentTypeField + "}")
                            .formatted(jsonString(title), jsonString(content))))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(201);
  }

  private static String extractId(String json) {
    int start = json.indexOf("\"id\":\"") + 6;
    int end = json.indexOf('"', start);
    return json.substring(start, end);
  }

  private static String jsonString(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
  }
}
