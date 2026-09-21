package com.example.searchapp.onboarding.service;

import com.example.searchapp.onboarding.dto.CreateDocumentRequest;
import com.example.searchapp.onboarding.entity.Document;
import com.example.searchapp.onboarding.exception.ClientNotFoundException;
import com.example.searchapp.onboarding.exception.DocumentNotFoundException;
import com.example.searchapp.onboarding.repository.ClientRepository;
import com.example.searchapp.onboarding.repository.DocumentRepository;
import com.example.searchapp.onboarding.service.model.Classification;
import com.example.searchapp.onboarding.service.model.DocumentChunkSet;
import com.example.searchapp.shared.TimedOperation;
import com.example.searchapp.shared.embedding.Embedder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Chunks, classifies and embeds outside the transaction, then writes the document row, its label
 * chunk and its body chunks atomically (§5.2). Unlike client creation, this has real orchestration
 * logic — that's why a service exists here and not for clients (§1.2).
 */
@Service
public class DocumentService {
  private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
  private final ClientRepository clients;
  private final DocumentRepository documents;
  private final DocumentClassifier classifier;
  private final Embedder embedder;
  private final SummaryWorker summaryWorker;
  private final ClassificationOutcomeMetrics classificationOutcomes;
  private final Timer embeddingTimer;

  public DocumentService(
      ClientRepository clients,
      DocumentRepository documents,
      DocumentClassifier classifier,
      Embedder embedder,
      SummaryWorker summaryWorker,
      ClassificationOutcomeMetrics classificationOutcomes,
      MeterRegistry meterRegistry) {
    this.clients = clients;
    this.documents = documents;
    this.classifier = classifier;
    this.embedder = embedder;
    this.summaryWorker = summaryWorker;
    this.classificationOutcomes = classificationOutcomes;
    embeddingTimer = meterRegistry.timer("document.embed");
  }

  public Document create(UUID clientId, CreateDocumentRequest request) {
    if (!clients.existsById(clientId)) {
      throw new ClientNotFoundException();
    }

    Classification classification =
        classifier.classify(
            request.title(), request.content(), request.documentType(), request.purposes());
    classificationOutcomes.record(classification);

    TimedOperation<DocumentChunkSet> embedded =
        TimedOperation.run(
            () ->
                DocumentChunkSet.embed(
                    embedder, request.title(), request.content(), classification.labelText()),
            embeddingTimer);

    Document document =
        documents.insert(
            clientId,
            request.title(),
            request.content(),
            classification,
            embedded.result().labelEmbedding(),
            embedded.result().bodyChunks(),
            embedder.modelId());
    log.info(
        "Document indexed document_id={} document_type={} classification_source={}"
            + " chunk_count={} embed_ms={}",
        document.id(),
        document.documentType(),
        document.classificationSource(),
        embedded.result().bodyChunks().size(),
        embedded.elapsedMillis());
    return document;
  }

  public Document find(UUID clientId, UUID documentId) {
    return documents.findById(clientId, documentId).orElseThrow(DocumentNotFoundException::new);
  }

  /** Requests a summary (§7.2). */
  public Document requestSummary(UUID clientId, UUID documentId) {
    return documents
        .requestSummary(clientId, documentId)
        .map(
            pending -> {
              summaryWorker.nudge();
              return pending;
            })
        // No row updated: not found, or a no-op (already pending/ready). Re-reading here rather
        // than returning a snapshot taken before the update means a request that raced another
        // one to the same none->pending transition still reports the row's real current state
        // (e.g. pending, claimed by the request that won) instead of a stale "none".
        .orElseGet(() -> find(clientId, documentId));
  }
}
