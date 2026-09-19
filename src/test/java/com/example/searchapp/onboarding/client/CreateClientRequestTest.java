package com.example.searchapp.onboarding.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CreateClientRequestTest {
  @Test
  void trimsFieldsAndUsesAnEmptyListWhenLinksAreMissing() {
    var request = new CreateClientRequest(" John ", " Doe ", " john@example.com ", "  ", null);

    assertThat(request.firstName()).isEqualTo("John");
    assertThat(request.lastName()).isEqualTo("Doe");
    assertThat(request.email()).isEqualTo("john@example.com");
    assertThat(request.description()).isNull();
    assertThat(request.socialLinks()).isEmpty();
  }

  @Test
  void acceptsOnlyAbsoluteHttpUrlsAndDottedEmailDomains() {
    var valid =
        new CreateClientRequest(
            "John",
            "Doe",
            "john@example.com",
            null,
            List.of("https://example.com/profile", "http://localhost:8080"));
    var invalid =
        new CreateClientRequest(
            "John", "Doe", "john@example", null, List.of("javascript:alert(1)", "relative"));

    assertThat(valid.validationErrors()).isEmpty();
    assertThat(invalid.validationErrors())
        .containsEntry("social_links[0]", "must be an absolute http or https URL")
        .containsEntry("social_links[1]", "must be an absolute http or https URL")
        .containsEntry("email", "must have a dotted domain");
  }
}
