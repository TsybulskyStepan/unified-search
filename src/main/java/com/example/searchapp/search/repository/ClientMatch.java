package com.example.searchapp.search.repository;

import java.util.UUID;

public record ClientMatch(UUID clientId, String field, double score) {}
