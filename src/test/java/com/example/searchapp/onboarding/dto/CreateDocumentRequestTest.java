package com.example.searchapp.onboarding.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CreateDocumentRequestTest {
  @Test
  void trimsTitleAndContent() {
    var request = new CreateDocumentRequest(" 2024 Utility Bill ", "\nAccount details here.\n");

    assertThat(request.title()).isEqualTo("2024 Utility Bill");
    assertThat(request.content()).isEqualTo("Account details here.");
  }

  @Test
  void toleratesNullFieldsForBeanValidationToReport() {
    var request = new CreateDocumentRequest(null, null);

    assertThat(request.title()).isNull();
    assertThat(request.content()).isNull();
  }
}
