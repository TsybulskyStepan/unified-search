package com.example.searchapp.onboarding.controller;

import com.example.searchapp.onboarding.dto.CreateDocumentRequest;
import com.example.searchapp.onboarding.entity.Document;
import com.example.searchapp.onboarding.service.DocumentService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/clients/{id}/documents")
public class DocumentController {
  private final DocumentService documents;

  public DocumentController(DocumentService documents) {
    this.documents = documents;
  }

  @PostMapping
  ResponseEntity<Document> create(
      @PathVariable UUID id, @Valid @RequestBody CreateDocumentRequest request) {
    Document document = documents.create(id, request);
    URI location = URI.create("/clients/" + id + "/documents/" + document.id());
    return ResponseEntity.created(location).body(document);
  }

  @GetMapping("/{documentId}")
  Document get(@PathVariable UUID id, @PathVariable UUID documentId) {
    return documents.find(id, documentId);
  }
}
