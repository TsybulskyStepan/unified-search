package com.example.searchapp.search.dto;

import com.example.searchapp.search.entity.SearchClient;
import com.example.searchapp.search.entity.SearchDocument;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchResult(
    String type,
    BigDecimal score,
    SearchMatch match,
    SearchClient client,
    SearchDocument document) {}
