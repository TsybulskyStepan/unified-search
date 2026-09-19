package com.example.searchapp.onboarding.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Client(
    UUID id,
    String firstName,
    String lastName,
    String email,
    String description,
    List<String> socialLinks,
    Instant createdAt) {}
