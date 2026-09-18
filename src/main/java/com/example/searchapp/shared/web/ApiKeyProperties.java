package com.example.searchapp.shared.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record ApiKeyProperties(String apiKey) {
  public ApiKeyProperties {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException("API_KEY must be configured");
    }
    if (apiKey.length() < 32) {
      throw new IllegalArgumentException("API_KEY must be at least 32 characters");
    }
  }
}
