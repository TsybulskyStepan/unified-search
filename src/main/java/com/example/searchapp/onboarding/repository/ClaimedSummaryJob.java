package com.example.searchapp.onboarding.repository;

import java.util.UUID;

/** One row claimed by {@link DocumentRepository#claimPending} (§7.2). */
public record ClaimedSummaryJob(UUID id, String title, String content, int attempts) {}
