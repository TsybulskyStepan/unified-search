package com.example.searchapp.search.repository;

import java.util.UUID;

public record DocumentMatch(UUID documentId, int startOffset, int endOffset, double score) {}
