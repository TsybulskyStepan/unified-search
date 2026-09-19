package com.example.searchapp.search.repository;

import com.example.searchapp.search.entity.SearchDocument;

public record HydratedDocument(SearchDocument document, String passage) {}
