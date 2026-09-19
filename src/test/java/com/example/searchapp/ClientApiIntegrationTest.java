package com.example.searchapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
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
  void reportsValidationAndTreatsMalformedIdsAsNotFound() throws Exception {
    var invalid =
        post(
            port,
            "/clients",
            TEST_API_KEY,
            "{\"first_name\":\" \",\"last_name\":\"Doe\",\"email\":\"bad\",\"social_links\":[\"javascript:alert(1)\"]}");
    assertThat(invalid.statusCode()).isEqualTo(400);
    assertThat(invalid.body()).contains("errors").contains("first_name").contains("email");

    var malformed = get(port, "/clients/not-a-uuid", TEST_API_KEY);
    assertThat(malformed.statusCode()).isEqualTo(404);
    var unknown = get(port, "/clients/00000000-0000-0000-0000-000000000000", TEST_API_KEY);
    assertThat(unknown.statusCode()).isEqualTo(404);
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
}
