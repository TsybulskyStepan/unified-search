package com.example.searchapp.onboarding.service.model;

import com.example.searchapp.onboarding.service.Chunker;
import com.example.searchapp.shared.embedding.Embedder;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything a document contributes to the vector index: one label embedding and one embedding per
 * body chunk. The label and every chunk are embedded in a single batch, and this is the only place
 * that knows how that batch is laid out, so no caller can misalign a vector with its text.
 */
public record DocumentChunkSet(float[] labelEmbedding, List<EmbeddedChunk> bodyChunks) {

  public static DocumentChunkSet embed(
      Embedder embedder, String title, String content, String labelText) {
    List<Chunk> chunks = Chunker.split(content);
    // Content is non-blank, so the chunker always yields at least one chunk. A document that exists
    // but cannot be found is the worst outcome here and a silent one, so a broken assumption fails
    // loudly instead.
    if (chunks.isEmpty()) {
      throw new IllegalStateException("Chunker produced no chunks for non-blank content");
    }

    List<String> inputs = new ArrayList<>(chunks.size() + 1);
    inputs.add(Chunker.labelEmbeddingInput(title, labelText));
    for (Chunk chunk : chunks) {
      inputs.add(Chunker.embeddingInput(title, content, chunk));
    }
    List<float[]> embeddings = embedder.embedAll(inputs);

    List<EmbeddedChunk> bodyChunks = new ArrayList<>(chunks.size());
    for (int i = 0; i < chunks.size(); i++) {
      bodyChunks.add(new EmbeddedChunk(chunks.get(i), embeddings.get(i + 1)));
    }
    return new DocumentChunkSet(embeddings.getFirst(), bodyChunks);
  }

  /** Re-embeds only the label, for a document whose labels changed but whose content did not. */
  public static float[] embedLabel(Embedder embedder, String title, String labelText) {
    return embedder.embed(Chunker.labelEmbeddingInput(title, labelText));
  }
}
