package com.example.searchapp.onboarding.repository.model;

import java.util.UUID;

public record ClaimedSummaryJob(UUID id, String title, String content, int attempts) {}
