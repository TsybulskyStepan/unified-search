package com.example.searchapp.search.planner;

import java.util.UUID;

/** A client a query plan recognised as an identity reference. */
public record ClientMention(UUID clientId, String field, double score) {}
