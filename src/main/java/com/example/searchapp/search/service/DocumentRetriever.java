package com.example.searchapp.search.service;

import com.example.searchapp.search.planner.QueryPlan;
import com.example.searchapp.search.repository.DocumentMatch;
import com.example.searchapp.search.repository.DocumentSearchRepository;
import com.example.searchapp.search.repository.HydratedDocument;
import com.example.searchapp.search.repository.LabelDocumentMatch;
import com.example.searchapp.search.repository.RankedDocumentMatch;
import com.example.searchapp.shared.embedding.Embedder;
import com.example.searchapp.shared.embedding.QueryEmbedding;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Finds the documents a query's residual text asks for: label, lexical and semantic retrieval run
 * concurrently and are fused into one ranked list. The query is embedded once, and the resulting
 * vector is kept in the {@link RetrievalResult} so {@link #hydrate} can pick each match's best
 * passage without the caller ever holding it.
 *
 * <p>{@link #retrieve} blocks until its own fan-out completes. A caller with independent work, such
 * as the client query, must start it before calling.
 */
@Component
public class DocumentRetriever {
  private final DocumentSearchRepository documents;
  private final Embedder embedder;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  public DocumentRetriever(DocumentSearchRepository documents, Embedder embedder) {
    this.documents = documents;
    this.embedder = embedder;
  }

  /**
   * Retrieval is skipped, and {@link RetrievalResult#ran()} is false, when the plan has no residual
   * or the residual has nothing the lexical index can search for (only stop words).
   */
  public RetrievalResult retrieve(QueryPlan plan) {
    if (!plan.hasResidual() || !documents.hasSearchableTerms(plan.residual())) {
      return RetrievalResult.SKIPPED;
    }
    CompletableFuture<Timed<List<LabelDocumentMatch>>> labels =
        timedAsync(() -> documents.findLabelMatches(plan.types(), plan.purposes()));
    CompletableFuture<Timed<List<RankedDocumentMatch>>> lexical =
        timedAsync(() -> documents.findLexicalMatches(plan.residual()));
    CompletableFuture<Timed<QueryEmbedding>> embedded =
        timedAsync(() -> embedder.embedQuery(plan.residual()));
    CompletableFuture<Timed<List<RankedDocumentMatch>>> semantic =
        embedded.thenApplyAsync(
            embedding -> timed(() -> semanticMatches(embedding.value())), executor);

    Timed<List<LabelDocumentMatch>> labelResult = labels.join();
    Timed<List<RankedDocumentMatch>> lexicalResult = lexical.join();
    Timed<List<RankedDocumentMatch>> semanticResult = semantic.join();
    Timed<QueryEmbedding> embeddedResult = embedded.join();
    return new RetrievalResult(
        DocumentFusion.fuse(labelResult.value(), lexicalResult.value(), semanticResult.value()),
        Optional.of(embeddedResult.value().vector()),
        new RetrievalMeasurements(
            labelResult.nanos(),
            lexicalResult.nanos(),
            embeddedResult.nanos(),
            semanticResult.nanos(),
            labelResult.value().size(),
            lexicalResult.value().size(),
            semanticResult.value().size()));
  }

  /** Loads the given matches with the passage of each that best matches the retrieved query. */
  public Map<UUID, HydratedDocument> hydrate(List<DocumentMatch> matches, RetrievalResult result) {
    if (matches.isEmpty()) {
      return Map.of();
    }
    float[] queryVector =
        result
            .queryVector()
            .orElseThrow(
                () -> new IllegalStateException("Cannot hydrate matches: retrieval did not run"));
    return documents.findByMatches(matches, queryVector, embedder.modelId()).stream()
        .collect(Collectors.toMap(hydrated -> hydrated.document().id(), Function.identity()));
  }

  private List<RankedDocumentMatch> semanticMatches(QueryEmbedding embedding) {
    return embedding.readable()
        ? documents.findSemanticMatches(embedding.vector(), embedder.modelId())
        : List.of();
  }

  private <T> CompletableFuture<Timed<T>> timedAsync(Supplier<T> operation) {
    return CompletableFuture.supplyAsync(() -> timed(operation), executor);
  }

  private static <T> Timed<T> timed(Supplier<T> operation) {
    long startNanos = System.nanoTime();
    T value = operation.get();
    return new Timed<>(value, System.nanoTime() - startNanos);
  }

  @PreDestroy
  void closeExecutor() {
    executor.close();
  }

  private record Timed<T>(T value, long nanos) {}
}
