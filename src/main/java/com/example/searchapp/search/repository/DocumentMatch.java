package com.example.searchapp.search.repository;

import java.util.UUID;

public record DocumentMatch(
    UUID documentId, UUID clientId, int startOffset, int endOffset, double score) {}
