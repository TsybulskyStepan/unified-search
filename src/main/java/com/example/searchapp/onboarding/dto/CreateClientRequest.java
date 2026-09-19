package com.example.searchapp.onboarding.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CreateClientRequest(
    @NotBlank(message = "must not be blank") @Size(max = 100) String firstName,
    @NotBlank(message = "must not be blank") @Size(max = 100) String lastName,
    @NotBlank(message = "must not be blank")
        @Email(message = "must be a valid email address")
        @Size(max = 254)
        String email,
    @Size(max = 2_000) String description,
    @Size(max = 10)
        List<@NotBlank(message = "must not be blank") @Size(max = 2_048) String> socialLinks) {

  public CreateClientRequest {
    firstName = trim(firstName);
    lastName = trim(lastName);
    email = trim(email);
    description = trimToNull(description);
    socialLinks = normalizeLinks(socialLinks);
  }

  public Map<String, String> validationErrors() {
    Map<String, String> errors = new LinkedHashMap<>();
    for (int i = 0; i < socialLinks.size(); i++) {
      String link = socialLinks.get(i);
      if (!isHttpUrl(link)) {
        errors.put("social_links[" + i + "]", "must be an absolute http or https URL");
      }
    }
    if (email != null && !email.isBlank() && !hasDottedDomain(email)) {
      errors.put("email", "must have a dotted domain");
    }
    return errors;
  }

  private static String trim(String value) {
    return value == null ? null : value.trim();
  }

  private static String trimToNull(String value) {
    String trimmed = trim(value);
    return trimmed == null || trimmed.isEmpty() ? null : trimmed;
  }

  private static List<String> normalizeLinks(List<String> links) {
    if (links == null) {
      return List.of();
    }
    return links.stream().map(CreateClientRequest::trim).toList();
  }

  private static boolean hasDottedDomain(String value) {
    int at = value.lastIndexOf('@');
    return at > 0 && at < value.length() - 1 && value.indexOf('.', at + 1) > at + 1;
  }

  private static boolean isHttpUrl(String value) {
    if (value == null) {
      return false;
    }
    try {
      URI uri = new URI(value);
      String scheme = uri.getScheme();
      return uri.isAbsolute()
          && uri.getHost() != null
          && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
    } catch (URISyntaxException exception) {
      return false;
    }
  }
}
