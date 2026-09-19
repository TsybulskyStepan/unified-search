package com.example.searchapp.search;

import java.util.UUID;

record ClientMatch(UUID clientId, String field, double score) {}
