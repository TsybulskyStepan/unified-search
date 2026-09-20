package com.example.searchapp.search.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LabelDocumentMatch(
    UUID documentId, UUID clientId, Instant createdAt, List<String> labels) {}
