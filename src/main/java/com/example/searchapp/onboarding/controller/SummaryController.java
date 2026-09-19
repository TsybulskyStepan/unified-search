package com.example.searchapp.onboarding.controller;

import com.example.searchapp.onboarding.entity.Document;
import com.example.searchapp.onboarding.entity.SummaryStatus;
import com.example.searchapp.onboarding.service.DocumentService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST …/summary} (§4.1, §7.2): {@code 202} pending, {@code 200} ready. */
@RestController
@RequestMapping("/clients/{id}/documents/{documentId}/summary")
public class SummaryController {
  private final DocumentService documents;

  public SummaryController(DocumentService documents) {
    this.documents = documents;
  }

  @PostMapping
  ResponseEntity<Document> request(@PathVariable UUID id, @PathVariable UUID documentId) {
    Document document = documents.requestSummary(id, documentId);
    HttpStatus status =
        SummaryStatus.PENDING.equals(document.summaryStatus())
            ? HttpStatus.ACCEPTED
            : HttpStatus.OK;
    return ResponseEntity.status(status).body(document);
  }
}
