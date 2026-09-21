package com.example.searchapp.onboarding.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.searchapp.IntegrationTest;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

class ClientApiIntegrationTest extends IntegrationTest {
  @LocalServerPort private int port;

  @Test
  void createsFetchesAndRejectsCaseInsensitiveDuplicate() throws Exception {
    var create =
        post(
            port,
            "/clients",
            TEST_API_KEY,
            "{\"first_name\":\" John \",\"last_name\":\"Doe\",\"email\":\"john@example.com\"}");

    assertThat(create.statusCode()).isEqualTo(201);
    assertThat(create.headers().firstValue("Location")).isPresent();
    assertThat(create.body()).contains("\"first_name\":\"John\"").contains("\"social_links\":[]");

    String location = create.headers().firstValue("Location").orElseThrow();
    var fetched = get(port, URI.create(location).getPath(), TEST_API_KEY);
    assertThat(fetched.statusCode()).isEqualTo(200);
    assertThat(fetched.body()).contains("\"email\":\"john@example.com\"");

    var duplicate =
        post(
            port,
            "/clients",
            TEST_API_KEY,
            "{\"first_name\":\"Jane\",\"last_name\":\"Doe\",\"email\":\"JOHN@EXAMPLE.COM\"}");
    assertThat(duplicate.statusCode()).isEqualTo(409);
  }

  @Test
  void listsEveryClientWithItsIdOldestFirst() throws Exception {
    String first = createdId("list-first@example.com");
    String second = createdId("list-second@example.com");

    var response = get(port, "/clients", TEST_API_KEY);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body())
        .contains("\"id\":\"" + first + "\"")
        .contains("\"id\":\"" + second + "\"")
        .contains("\"email\":\"list-first@example.com\"");
    assertThat(response.body().indexOf(first)).isLessThan(response.body().indexOf(second));
    assertThat(get(port, "/clients", "wrong-key").statusCode()).isEqualTo(401);
  }

  private String createdId(String email) throws Exception {
    var created =
        post(
            port,
            "/clients",
            TEST_API_KEY,
            "{\"first_name\":\"List\",\"last_name\":\"Test\",\"email\":\"" + email + "\"}");
    assertThat(created.statusCode()).isEqualTo(201);
    String location = created.headers().firstValue("Location").orElseThrow();
    return location.substring(location.lastIndexOf('/') + 1);
  }

  @Test
  void reportsValidationAndRejectsMalformedIds() throws Exception {
    var invalid =
        post(
            port,
            "/clients",
            TEST_API_KEY,
            "{\"first_name\":\" \",\"last_name\":\"Doe\",\"email\":\"bad\",\"social_links\":[\"javascript:alert(1)\"]}");
    assertThat(invalid.statusCode()).isEqualTo(400);
    assertThat(invalid.body()).contains("errors").contains("first_name").contains("email");

    var malformed = get(port, "/clients/not-a-uuid", TEST_API_KEY);
    assertThat(malformed.statusCode()).isEqualTo(400);
    assertThat(malformed.body())
        .contains("identifier in the request path is malformed")
        .doesNotContain("UUID");
    var unknown = get(port, "/clients/00000000-0000-0000-0000-000000000000", TEST_API_KEY);
    assertThat(unknown.statusCode()).isEqualTo(404);
  }

  @Test
  void reportsHandRolledValidationFailuresInTheSameShapeAsBeanValidation() throws Exception {
    var response =
        post(
            port,
            "/clients",
            TEST_API_KEY,
            "{\"first_name\":\"John\",\"last_name\":\"Doe\",\"email\":\"john@localhost\","
                + "\"social_links\":[\"ftp://example.com\"]}");

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body())
        .contains("\"errors\"")
        .contains("\"email\":\"must have a dotted domain\"")
        .contains("\"social_links[0]\":\"must be an absolute http or https URL\"");
  }

  @Test
  void rejectsBodyLargerThanTheRequestCap() throws Exception {
    String body =
        "{\"first_name\":\"John\",\"last_name\":\"Doe\",\"email\":\"john-"
            + "a".repeat(263_000)
            + "@example.com\"}";
    var response = post(port, "/clients", TEST_API_KEY, body);
    assertThat(response.statusCode()).isEqualTo(413);
  }

  @Test
  void allowsBodyWithoutContentLengthBeyondTheSoftCap() throws Exception {
    String body =
        "{\"first_name\":\"John\",\"last_name\":\"Doe\",\"email\":\"chunked@example.com\"}"
            + " ".repeat(263_000);
    var publisher =
        HttpRequest.BodyPublishers.ofInputStream(
            () -> new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    assertThat(publisher.contentLength()).isEqualTo(-1);

    var response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/clients"))
                    .version(HttpClient.Version.HTTP_1_1)
                    .header("X-API-Key", TEST_API_KEY)
                    .header("Content-Type", "application/json")
                    .POST(publisher)
                    .build(),
                HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(response.body()).contains("\"email\":\"chunked@example.com\"");
  }
}
