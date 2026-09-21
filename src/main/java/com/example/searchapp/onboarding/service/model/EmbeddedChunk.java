package com.example.searchapp.onboarding.service.model;

/**
 * A chunk paired with its embedding, so the two never travel as separate parallel lists that could
 * drift out of index alignment between {@link DocumentService} and {@link DocumentRepository}.
 */
public record EmbeddedChunk(Chunk chunk, float[] embedding) {}
