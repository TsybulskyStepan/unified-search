package com.example.searchapp.onboarding.service.model;

/**
 * One window produced by Chunker. Carries code-point offsets into the document's content rather
 * than a copy of the text (§3.3) — the same shape the {@code document_chunk} table stores (§3.1),
 * and the SQL that hydrates a passage extracts by these same offsets (§6.6).
 */
public record Chunk(int ordinal, int startOffset, int endOffset) {}
