package com.example.searchapp.search.repository.model;

import java.util.UUID;

public record ClientMatch(UUID clientId, String field, MatchTier tier, double score) {}
