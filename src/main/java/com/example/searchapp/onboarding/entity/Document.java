package com.example.searchapp.onboarding.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Document(
    UUID id,
    UUID clientId,
    String title,
    String content,
    String summary,
    String summaryStatus,
    // (v2, §4.3) what the document is, which KYC questions it answers, and how that was decided.
    String documentType,
    List<String> purposes,
    String classificationSource,
    Instant createdAt) {}
