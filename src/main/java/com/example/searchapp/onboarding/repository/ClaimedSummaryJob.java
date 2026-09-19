package com.example.searchapp.onboarding.repository;

import java.util.UUID;

/**
 * One row claimed by {@link DocumentRepository#claimPending} (§7.2): everything {@code
 * SummaryWorker} needs to call the summarizer and log the outcome, without a second read.
 */
public record ClaimedSummaryJob(UUID id, String title, String content, int attempts) {}
