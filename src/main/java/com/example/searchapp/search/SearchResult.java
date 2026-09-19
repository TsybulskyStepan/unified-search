package com.example.searchapp.search;

import java.math.BigDecimal;

record SearchResult(String type, BigDecimal score, SearchMatch match, SearchClient client) {}
