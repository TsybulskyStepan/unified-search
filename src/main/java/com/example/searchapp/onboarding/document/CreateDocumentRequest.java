package com.example.searchapp.onboarding.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDocumentRequest(
    @NotBlank(message = "must not be blank") @Size(max = 300) String title,
    @NotBlank(message = "must not be blank") @Size(max = 64_000) String content) {

  public CreateDocumentRequest {
    title = trim(title);
    content = trim(content);
  }

  private static String trim(String value) {
    return value == null ? null : value.trim();
  }
}
