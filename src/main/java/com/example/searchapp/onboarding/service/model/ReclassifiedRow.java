package com.example.searchapp.onboarding.service.model;

import java.util.UUID;

/**
 * One row {@link Reclassifier} has finished computing a fresh {@link Classification} and label
 * embedding for, ready to persist (§3.4). Kept separate from the classification/embedding step so a
 * whole batch can be computed — CPU-bound work, no database involved — before any of it is written,
 * the same "embed outside the transaction" shape {@link DocumentService#create} uses (§5.2),
 * applied to a batch instead of a single document.
 */
public record ReclassifiedRow(
    UUID documentId, Classification classification, float[] labelEmbedding) {}
