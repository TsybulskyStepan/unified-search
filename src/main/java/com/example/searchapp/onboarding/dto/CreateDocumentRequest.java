package com.example.searchapp.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateDocumentRequest(
    @NotBlank(message = "must not be blank") @Size(max = 300) String title,
    @NotBlank(message = "must not be blank") @Size(max = 64_000) String content,
    // §4.2 (v2): optional, must be a taxonomy type id — checked against the taxonomy itself by
    // DocumentClassifier (§3.3), not here, since a plain DTO has no access to the loaded file.
    String documentType,
    // §4.2 (v2): optional, only meaningful alongside documentType; DocumentClassifier enforces that
    // pairing for the same reason.
    List<String> purposes) {

  public CreateDocumentRequest {
    title = trim(title);
    content = trim(content);
    documentType = trimToNull(documentType);
    purposes = normalize(purposes);
  }

  /** Convenience for callers with no classification opinion (seeding, most tests). */
  public CreateDocumentRequest(String title, String content) {
    this(title, content, null, List.of());
  }

  private static String trim(String value) {
    return value == null ? null : value.trim();
  }

  private static String trimToNull(String value) {
    String trimmed = trim(value);
    return trimmed == null || trimmed.isEmpty() ? null : trimmed;
  }

  private static List<String> normalize(List<String> purposes) {
    if (purposes == null) {
      return List.of();
    }
    return purposes.stream().map(CreateDocumentRequest::trim).toList();
  }
}
