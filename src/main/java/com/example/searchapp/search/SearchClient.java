package com.example.searchapp.search;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record SearchClient(
    UUID id,
    String firstName,
    String lastName,
    String email,
    String description,
    List<String> socialLinks,
    Instant createdAt) {}
