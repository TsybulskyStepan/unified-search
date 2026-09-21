package com.example.searchapp.shared.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ApiKeyPropertiesTest {
  @Test
  void rejectsMissingApiKey() {
    assertThatThrownBy(() -> new ApiKeyProperties(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("API_KEY");
  }

  @Test
  void rejectsShortApiKey() {
    assertThatThrownBy(() -> new ApiKeyProperties("too-short"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("32 characters");
  }
}
