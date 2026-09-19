package com.example.searchapp.onboarding.document;

import com.example.searchapp.onboarding.client.ClientNotFoundException;
import com.example.searchapp.onboarding.client.ClientRepository;
import com.example.searchapp.onboarding.exception.DocumentNotFoundException;
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

  public DocumentService(
      ClientRepository clients, DocumentRepository documents, Embedder embedder) {
    this.clients = clients;
    this.documents = documents;
    this.embedder = embedder;
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
}
