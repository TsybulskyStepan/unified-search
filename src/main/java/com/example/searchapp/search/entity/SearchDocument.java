package com.example.searchapp.search.entity;

import java.time.Instant;
import java.util.UUID;

public record SearchDocument(
    UUID id,
    UUID clientId,
    String clientName,
    String title,
    String summary,
    String summaryStatus,
    Instant createdAt) {}
