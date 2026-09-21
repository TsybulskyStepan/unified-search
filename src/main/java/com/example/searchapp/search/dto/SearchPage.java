package com.example.searchapp.search.dto;

import java.util.List;

public record SearchPage(List<SearchResult> results, int total) {}
