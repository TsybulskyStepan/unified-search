package com.example.searchapp.search.service;

import com.example.searchapp.search.planner.QueryPlan;
import com.example.searchapp.search.repository.DocumentSearchRepository;
import com.example.searchapp.search.repository.model.DocumentMatch;
import com.example.searchapp.search.repository.model.HydratedDocument;
import com.example.searchapp.search.repository.model.LabelDocumentMatch;
import com.example.searchapp.search.repository.model.RankedDocumentMatch;
import com.example.searchapp.shared.TimedOperation;
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
    CompletableFuture<TimedOperation<List<LabelDocumentMatch>>> labels =
        timedAsync(() -> documents.findLabelMatches(plan.types(), plan.purposes()));
    CompletableFuture<TimedOperation<List<RankedDocumentMatch>>> lexical =
        timedAsync(() -> documents.findLexicalMatches(plan.residual()));
    CompletableFuture<TimedOperation<QueryEmbedding>> embedded =
        timedAsync(() -> embedder.embedQuery(plan.residual()));
    CompletableFuture<TimedOperation<List<RankedDocumentMatch>>> semantic =
        embedded.thenApplyAsync(
            embedding -> TimedOperation.run(() -> semanticMatches(embedding.result())), executor);

    TimedOperation<List<LabelDocumentMatch>> labelResult = labels.join();
    TimedOperation<List<RankedDocumentMatch>> lexicalResult = lexical.join();
    TimedOperation<List<RankedDocumentMatch>> semanticResult = semantic.join();
    TimedOperation<QueryEmbedding> embeddedResult = embedded.join();
    return new RetrievalResult(
        DocumentFusion.fuse(labelResult.result(), lexicalResult.result(), semanticResult.result()),
        Optional.of(embeddedResult.result().vector()),
        new RetrievalMeasurements(
            labelResult.elapsedNanos(),
            lexicalResult.elapsedNanos(),
            embeddedResult.elapsedNanos(),
            semanticResult.elapsedNanos(),
            labelResult.result().size(),
            lexicalResult.result().size(),
            semanticResult.result().size()));
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

  private <T> CompletableFuture<TimedOperation<T>> timedAsync(Supplier<T> operation) {
    return CompletableFuture.supplyAsync(() -> TimedOperation.run(operation), executor);
  }

  @PreDestroy
  void closeExecutor() {
    executor.close();
  }
}
