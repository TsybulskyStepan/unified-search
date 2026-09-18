package com.example.searchapp;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.searchapp.shared.web.ApiKeyProperties;
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
