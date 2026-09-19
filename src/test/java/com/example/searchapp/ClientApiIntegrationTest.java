package com.example.searchapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

class ClientApiIntegrationTest extends IntegrationTest {
  private static final String API_KEY = "test-api-key-that-is-at-least-32-characters";
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  @LocalServerPort private int port;

  @Test
  void createsFetchesAndRejectsCaseInsensitiveDuplicate() throws Exception {
    var create =
        send(
            request(
                "POST",
                "/clients",
                "{\"first_name\":\" John \",\"last_name\":\"Doe\",\"email\":\"john@example.com\"}"));

    assertThat(create.statusCode()).isEqualTo(201);
    assertThat(create.headers().firstValue("Location")).isPresent();
    assertThat(create.body()).contains("\"first_name\":\"John\"").contains("\"social_links\":[]");

    String location = create.headers().firstValue("Location").orElseThrow();
    var fetched = send(request("GET", URI.create(location).getPath(), null));
    assertThat(fetched.statusCode()).isEqualTo(200);
    assertThat(fetched.body()).contains("\"email\":\"john@example.com\"");

    var duplicate =
        send(
            request(
                "POST",
                "/clients",
                "{\"first_name\":\"Jane\",\"last_name\":\"Doe\",\"email\":\"JOHN@EXAMPLE.COM\"}"));
    assertThat(duplicate.statusCode()).isEqualTo(409);
  }

  @Test
  void reportsValidationAndTreatsMalformedIdsAsNotFound() throws Exception {
    var invalid =
        send(
            request(
                "POST",
                "/clients",
                "{\"first_name\":\" \",\"last_name\":\"Doe\",\"email\":\"bad\",\"social_links\":[\"javascript:alert(1)\"]}"));
    assertThat(invalid.statusCode()).isEqualTo(400);
    assertThat(invalid.body()).contains("errors").contains("first_name").contains("email");

    var malformed = send(request("GET", "/clients/not-a-uuid", null));
    assertThat(malformed.statusCode()).isEqualTo(404);
    var unknown = send(request("GET", "/clients/00000000-0000-0000-0000-000000000000", null));
    assertThat(unknown.statusCode()).isEqualTo(404);
  }

  @Test
  void rejectsBodyLargerThanTheRequestCap() throws Exception {
    String body =
        "{\"first_name\":\"John\",\"last_name\":\"Doe\",\"email\":\"john-"
            + "a".repeat(263_000)
            + "@example.com\"}";
    var response = send(request("POST", "/clients", body));
    assertThat(response.statusCode()).isEqualTo(413);
  }

  private HttpRequest.Builder request(String method, String path, String body) {
    return request(method, URI.create("http://localhost:" + port + path), body);
  }

  private HttpRequest.Builder request(String method, URI uri, String body) {
    var builder = HttpRequest.newBuilder(uri).header("X-API-Key", API_KEY);
    if ("POST".equals(method)) {
      builder
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body));
    } else {
      builder.GET();
    }
    return builder;
  }

  private HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
    return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }
}
