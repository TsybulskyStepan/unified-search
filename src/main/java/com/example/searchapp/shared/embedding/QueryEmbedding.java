package com.example.searchapp.shared.embedding;

/**
 * A query's vector and whether the model could read it as words (§6.3). Unreadable text still has a
 * vector, and that vector lands near arbitrary chunks, so semantic retrieval must not trust it.
 */
public record QueryEmbedding(float[] vector, boolean readable) {}
