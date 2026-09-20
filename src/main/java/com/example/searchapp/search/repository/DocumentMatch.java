package com.example.searchapp.search.repository;

import java.util.List;
import java.util.UUID;

public record DocumentMatch(
    UUID documentId, UUID clientId, double score, List<String> signals, List<String> labels) {}
