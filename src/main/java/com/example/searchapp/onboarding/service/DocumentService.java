package com.example.searchapp.onboarding.service;

import com.example.searchapp.onboarding.dto.CreateDocumentRequest;
import com.example.searchapp.onboarding.entity.Document;
import com.example.searchapp.onboarding.exception.ClientNotFoundException;
import com.example.searchapp.onboarding.exception.DocumentNotFoundException;
import com.example.searchapp.onboarding.repository.ClientRepository;
import com.example.searchapp.onboarding.repository.DocumentRepository;
import com.example.searchapp.shared.embedding.Embedder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Chunks and embeds outside the transaction, then writes the document row and its chunks atomically
 * (§5.2). Unlike client creation, this has real orchestration logic — that's why a service exists
 * here and not for clients (§1.2).
 */
@Service
public class DocumentService {
  private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
  private final ClientRepository clients;
  private final DocumentRepository documents;
  private final Embedder embedder;
  private final SummaryWorker summaryWorker;
  private final Timer embeddingTimer;

  public DocumentService(
      ClientRepository clients,
      DocumentRepository documents,
      Embedder embedder,
      SummaryWorker summaryWorker,
      MeterRegistry meterRegistry) {
    this.clients = clients;
    this.documents = documents;
    this.embedder = embedder;
    this.summaryWorker = summaryWorker;
    embeddingTimer = meterRegistry.timer("document.embed");
  }

  public Document create(UUID clientId, CreateDocumentRequest request) {
    if (!clients.existsById(clientId)) {
      throw new ClientNotFoundException();
    }

    List<Chunk> chunks = Chunker.split(request.content());
    // Content is non-blank (@NotBlank), so the chunker always yields at least one chunk (§3.2).
    // A document that exists but cannot be found is the worst outcome in this system, and it is
    // silent — this turns a broken assumption into a loud failure instead.
    if (chunks.isEmpty()) {
      throw new IllegalStateException("Chunker produced no chunks for non-blank content");
    }

    List<String> embeddingInputs =
        chunks.stream()
            .map(chunk -> Chunker.embeddingInput(request.title(), request.content(), chunk))
            .toList();
    long embeddingStartNanos = System.nanoTime();
    long embeddingNanos;
    List<float[]> embeddings;
    try {
      embeddings = embedder.embedAll(embeddingInputs);
    } finally {
      embeddingNanos = System.nanoTime() - embeddingStartNanos;
      embeddingTimer.record(embeddingNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
    }

    List<EmbeddedChunk> embeddedChunks =
        IntStream.range(0, chunks.size())
            .mapToObj(i -> new EmbeddedChunk(chunks.get(i), embeddings.get(i)))
            .toList();

    Document document =
        documents.insert(
            clientId, request.title(), request.content(), embeddedChunks, embedder.modelId());
    log.info(
        "Document indexed document_id={} chunk_count={} embed_ms={}",
        document.id(),
        chunks.size(),
        java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(embeddingNanos));
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
