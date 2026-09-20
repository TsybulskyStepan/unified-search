package com.example.searchapp.search.planner;

import java.util.UUID;

/** A leading-run identity match supplied by the client store for query-plan resolution. */
public record MentionCandidate(
    UUID clientId, String field, double score, int matchedThroughPosition) {}
