package com.example.searchapp.onboarding.service;

import com.example.searchapp.onboarding.dto.CreateDocumentRequest;
import com.example.searchapp.onboarding.entity.Document;
import com.example.searchapp.onboarding.exception.ClientNotFoundException;
import com.example.searchapp.onboarding.exception.DocumentNotFoundException;
import com.example.searchapp.onboarding.repository.ClientRepository;
import com.example.searchapp.onboarding.repository.DocumentRepository;
import com.example.searchapp.shared.embedding.Embedder;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;

/**
 * Chunks and embeds outside the transaction, then writes the document row and its chunks atomically
 * (§5.2). Unlike client creation, this has real orchestration logic — that's why a service exists
 * here and not for clients (§1.2).
 */
@Service
public class DocumentService {
  private final ClientRepository clients;
  private final DocumentRepository documents;
  private final Embedder embedder;
  private final SummaryWorker summaryWorker;

  public DocumentService(
      ClientRepository clients,
      DocumentRepository documents,
      Embedder embedder,
      SummaryWorker summaryWorker) {
    this.clients = clients;
    this.documents = documents;
    this.embedder = embedder;
    this.summaryWorker = summaryWorker;
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
    List<float[]> embeddings = embedder.embedAll(embeddingInputs);

    List<EmbeddedChunk> embeddedChunks =
        IntStream.range(0, chunks.size())
            .mapToObj(i -> new EmbeddedChunk(chunks.get(i), embeddings.get(i)))
            .toList();

    return documents.insert(
        clientId, request.title(), request.content(), embeddedChunks, embedder.modelId());
  }

  public Document find(UUID clientId, UUID documentId) {
    return documents.findById(clientId, documentId).orElseThrow(DocumentNotFoundException::new);
  }

  /**
   * Requests a summary (§7.2). {@code none}/{@code failed} transitions to {@code pending} and
   * nudges the worker; already {@code pending} or {@code ready} is a no-op that returns the current
   * row unchanged — repeating the request can neither double-enqueue nor reset a job already in
   * flight.
   */
  public Document requestSummary(UUID clientId, UUID documentId) {
    Document current = find(clientId, documentId);
    return documents
        .requestSummary(clientId, documentId)
        .map(
            pending -> {
              summaryWorker.nudge();
              return pending;
            })
        .orElse(current);
  }
}
