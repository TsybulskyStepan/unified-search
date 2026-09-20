package com.example.searchapp.search.planner;

/** A normalized query token retaining whether the user wrote it possessively. */
public record NormalizedToken(String text, boolean possessive) {}
