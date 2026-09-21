package com.example.searchapp.search.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.searchapp.IntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

class SearchClientApiIntegrationTest extends IntegrationTest {
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  @LocalServerPort private int port;

  @Test
  void findsAnEmailDomainAndReportsEmailAsTheMatchedField() throws Exception {
    createClient(
        "{\"first_name\":\"John\",\"last_name\":\"Doe\",\"email\":\"john.doe@neviswealth.com\"}");

    var response = get(port, "/search?q=NevisWealth", TEST_API_KEY);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("X-Total-Count")).contains("1");
    assertThat(response.body())
        .contains("\"type\":\"client\"")
        .contains("\"field\":\"email\"")
        .contains("\"tier\":\"identity\"")
        .contains("\"email\":\"john.doe@neviswealth.com\"");
  }

  @Test
  void findsACompanyUrlAndReportsSocialLinksAsTheMatchedField() throws Exception {
    createClient(
        """
        {"first_name":"Jane","last_name":"Smith","email":"jane@example.com",
         "social_links":["https://www.linkedin.com/company/socialmatchcorp"]}
        """);

    var response = get(port, "/search?q=socialmatchcorp", TEST_API_KEY);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("X-Total-Count")).contains("1");
    assertThat(response.body())
        .contains("\"field\":\"social_links\"")
        .contains("\"tier\":\"identity\"")
        .contains("\"email\":\"jane@example.com\"");
  }

  @Test
  void findsAMisspelledSurname() throws Exception {
    createClient(
        "{\"first_name\":\"Mary\",\"last_name\":\"Henderson\",\"email\":\"mary@example.com\"}");

    var response = get(port, "/search?q=Hendersen", TEST_API_KEY);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("X-Total-Count")).contains("1");
    assertThat(response.body())
        .contains("\"field\":\"name\"")
        .contains("\"email\":\"mary@example.com\"");
  }

  @Test
  void excludesUnrelatedQueriesInsteadOfReturningTheLeastSimilarClient() throws Exception {
    var response = get(port, "/search?q=unrelated-term", TEST_API_KEY);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("X-Total-Count")).contains("0");
    assertThat(response.body()).isEqualTo("[]");
  }

  @Test
  void treatsSqlControlCharactersInTheQueryAsSearchText() throws Exception {
    var response = get(port, "/search?q=%27%20OR%201%3D1%20--", TEST_API_KEY);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("X-Total-Count")).contains("0");
    assertThat(response.body()).isEqualTo("[]");
  }

  @Test
  void paginatesTheAboveFloorCandidatesAndValidatesPagingParameters() throws Exception {
    createClient(
        "{\"first_name\":\"Cora\",\"last_name\":\"Gamma\",\"email\":\"cora@pageexample.test\"}");
    createClient(
        "{\"first_name\":\"Bella\",\"last_name\":\"Beta\",\"email\":\"bella@pageexample.test\"}");
    createClient(
        "{\"first_name\":\"Anna\",\"last_name\":\"Alpha\",\"email\":\"anna@pageexample.test\"}");

    var first = get(port, "/search?q=pageexample&limit=1&offset=0", TEST_API_KEY);
    var second = get(port, "/search?q=pageexample&limit=1&offset=1", TEST_API_KEY);
    var third = get(port, "/search?q=pageexample&limit=1&offset=2", TEST_API_KEY);
    var beyond = get(port, "/search?q=pageexample&limit=1&offset=3", TEST_API_KEY);

    assertThat(first.headers().firstValue("X-Total-Count")).contains("3");
    assertThat(first.body()).contains("\"email\":\"anna@pageexample.test\"");
    assertThat(second.body()).contains("\"email\":\"bella@pageexample.test\"");
    assertThat(third.body()).contains("\"email\":\"cora@pageexample.test\"");
    assertThat(beyond.body()).isEqualTo("[]");
    assertThat(beyond.headers().firstValue("X-Total-Count")).contains("3");

    assertThat(get(port, "/search?q=%20", TEST_API_KEY).statusCode()).isEqualTo(400);
    assertThat(get(port, "/search?q=pageexample&limit=51", TEST_API_KEY).statusCode())
        .isEqualTo(400);
    assertThat(get(port, "/search?q=pageexample&offset=-1", TEST_API_KEY).statusCode())
        .isEqualTo(400);
  }

  @Test
  void aFullNameRanksItsExactMatchAboveClientsSharingTheFirstName() throws Exception {
    createSharedFirstNameClients("Wilhelmina");

    var response = get(port, "/search?q=Wilhelmina%20Okafor", TEST_API_KEY);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(firstEmail(response.body())).isEqualTo("wilhelmina.okafor@example.com");
  }

  @Test
  void aPartialSurnameStillRanksTheBestMatchAboveClientsSharingTheFirstName() throws Exception {
    createSharedFirstNameClients("Ottoline");

    var response = get(port, "/search?q=Ottoline%20Ok", TEST_API_KEY);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(firstEmail(response.body())).isEqualTo("ottoline.okafor@example.com");
  }

  private void createSharedFirstNameClients(String first) throws Exception {
    for (String last :
        new String[] {"Brandt", "Castellano", "Duvall", "Eriksen", "Fairbanks", "Okafor"}) {
      createClient(
          "{\"first_name\":\""
              + first
              + "\",\"last_name\":\""
              + last
              + "\",\"email\":\""
              + first.toLowerCase()
              + "."
              + last.toLowerCase()
              + "@example.com\"}");
    }
  }

  private static String firstEmail(String body) {
    var matcher = Pattern.compile("\"email\":\"([^\"]+)\"").matcher(body);
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  private void createClient(String body) throws Exception {
    var response =
        HTTP.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/clients"))
                .header("X-API-Key", TEST_API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(201);
  }
}
