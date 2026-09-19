package com.example.searchapp.search.dto;

import com.example.searchapp.search.entity.SearchClient;
import java.math.BigDecimal;

public record SearchResult(String type, BigDecimal score, SearchMatch match, SearchClient client) {}
