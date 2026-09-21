package com.example.searchapp.search.service;

/** Per-stage durations and how many candidates each signal produced. */
public record RetrievalMeasurements(
    long labelNanos,
    long lexicalNanos,
    long queryEmbeddingNanos,
    long semanticNanos,
    int labelHits,
    int lexicalHits,
    int semanticHits) {}
