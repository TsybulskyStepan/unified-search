package com.example.searchapp;

import static org.assertj.core.api.Assertions.assertThat;

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
    assertThat(response.headers().firstValue("X-Total-Count")).contains("1");
    assertThat(response.body())
        .contains("\"type\":\"document\"")
        .contains("\"title\":\"2024 Utility Bill\"")
        .contains("\"client_name\":\"Jane Doe\"")
        .contains("\"passage\":\"account holder's name and confirms occupancy")
        .doesNotContain("\"type\":\"client\"")
        .doesNotContain("\"content\"");
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
