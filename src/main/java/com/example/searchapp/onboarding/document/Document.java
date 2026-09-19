package com.example.searchapp.onboarding.document;

import java.time.Instant;
import java.util.UUID;

public record Document(
    UUID id,
    UUID clientId,
    String title,
    String content,
    String summary,
    String summaryStatus,
    Instant createdAt) {}
