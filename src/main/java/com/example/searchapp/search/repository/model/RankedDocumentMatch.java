package com.example.searchapp.search.repository.model;

import java.time.Instant;
import java.util.UUID;

public record RankedDocumentMatch(
    UUID documentId, UUID clientId, Instant createdAt, double score) {}
