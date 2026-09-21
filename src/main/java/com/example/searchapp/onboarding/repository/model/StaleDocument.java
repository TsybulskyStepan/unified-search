package com.example.searchapp.onboarding.repository.model;

import java.util.List;
import java.util.UUID;

/**
 * One row {@code Reclassifier} found at a {@code taxonomy_version} below the current one (§3.4):
 * enough of the document to either re-run rule classification ({@code title}, {@code content}) or
 * keep an existing {@code request}-sourced type while refreshing its label.
 */
public record StaleDocument(
    UUID id,
    String title,
    String content,
    String documentType,
    List<String> purposes,
    String classificationSource) {}
