package com.example.searchapp.search.repository.model;

import java.util.List;
import java.util.UUID;

public record DocumentMatch(
    UUID documentId, UUID clientId, double score, List<Signal> signals, List<String> labels) {}
