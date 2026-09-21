package com.example.searchapp.onboarding.seed;

import java.util.List;

public record DemoClient(
    String firstName,
    String lastName,
    String email,
    String description,
    List<String> socialLinks,
    List<DemoDocument> documents) {}
