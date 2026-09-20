package com.example.searchapp.onboarding.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
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

  @Test
  void defaultsDocumentTypeAndPurposesWhenOmitted() {
    var request = new CreateDocumentRequest("Title", "Content");

    assertThat(request.documentType()).isNull();
    assertThat(request.purposes()).isEmpty();
  }

  @Test
  void trimsDocumentTypeToNullWhenBlank() {
    var request = new CreateDocumentRequest("Title", "Content", "   ", null);

    assertThat(request.documentType()).isNull();
  }

  @Test
  void trimsEachRequestedPurpose() {
    var request =
        new CreateDocumentRequest(
            "Title", "Content", "utility_bill", Arrays.asList(" proof_of_address ", "tax_status"));

    assertThat(request.purposes()).containsExactly("proof_of_address", "tax_status");
  }

  @Test
  void toleratesANullPurposesList() {
    var request = new CreateDocumentRequest("Title", "Content", "utility_bill", null);

    assertThat(request.purposes()).isEqualTo(List.of());
  }
}
