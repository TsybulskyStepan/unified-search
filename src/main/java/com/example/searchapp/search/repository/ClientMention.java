package com.example.searchapp.search.repository;

import java.util.UUID;

public record ClientMention(UUID clientId, String field, double score, boolean hasResidual) {}
