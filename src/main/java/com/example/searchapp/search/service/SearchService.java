package com.example.searchapp.search.service;

import com.example.searchapp.search.dto.SearchMatch;
import com.example.searchapp.search.dto.SearchRequest;
import com.example.searchapp.search.dto.SearchResult;
import com.example.searchapp.search.entity.SearchClient;
import com.example.searchapp.search.repository.ClientMatch;
import com.example.searchapp.search.repository.ClientMention;
import com.example.searchapp.search.repository.ClientSearchRepository;
import com.example.searchapp.search.repository.DocumentMatch;
import com.example.searchapp.search.repository.DocumentSearchRepository;
import com.example.searchapp.search.repository.HydratedDocument;
import com.example.searchapp.shared.embedding.Embedder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class SearchService {
  private static final Logger log = LoggerFactory.getLogger(SearchService.class);
  private final ClientSearchRepository clients;
  private final DocumentSearchRepository documents;
  private final Embedder embedder;
  private final Timer lexicalTimer;
  private final Timer queryEmbeddingTimer;
  private final Timer semanticTimer;
  private final Timer totalTimer;
  private final ExecutorService searchExecutor = Executors.newVirtualThreadPerTaskExecutor();

  public SearchService(
      ClientSearchRepository clients,
      DocumentSearchRepository documents,
      Embedder embedder,
      MeterRegistry meterRegistry) {
    this.clients = clients;
    this.documents = documents;
    this.embedder = embedder;
    lexicalTimer = meterRegistry.timer("search.lexical");
    queryEmbeddingTimer = meterRegistry.timer("search.embed_query");
    semanticTimer = meterRegistry.timer("search.semantic");
    totalTimer = meterRegistry.timer("search.total");
  }

  public SearchPage search(SearchRequest request) {
    long totalStartNanos = System.nanoTime();
    SearchTimings timings = new SearchTimings();
    int lexicalHits = 0;
    int semanticHits = 0;
    int returned = 0;
    try {
      List<String> queryTokens = queryTokens(request.query());
      CompletableFuture<List<ClientMatch>> clientMatches =
          CompletableFuture.supplyAsync(
              () ->
                  time(
                      lexicalTimer,
                      timings::setLexicalNanos,
                      () -> clients.findMatches(request.query())),
              searchExecutor);
      CompletableFuture<List<ClientMention>> clientMentions =
          CompletableFuture.supplyAsync(() -> clients.findMentions(queryTokens), searchExecutor);
      CompletableFuture<List<DocumentMatch>> documentMatches =
          CompletableFuture.supplyAsync(
              () -> {
                float[] queryVector =
                    time(
                        queryEmbeddingTimer,
                        timings::setQueryEmbeddingNanos,
                        () -> embedder.embed(request.query()));
                return time(
                    semanticTimer,
                    timings::setSemanticNanos,
                    () -> documents.findMatches(queryVector, embedder.modelId()));
              },
              searchExecutor);

      CompletableFuture.allOf(clientMatches, clientMentions, documentMatches).join();
      List<ClientMatch> clientResults = clientMatches.join();
      List<ClientMention> mentionResults = clientMentions.join();
      List<DocumentMatch> documentResults = documentMatches.join();
      lexicalHits = clientResults.size();
      semanticHits = documentResults.size();

      List<ResultOrdering.Candidate> candidates =
          ResultOrdering.order(clientResults, documentResults, mentionResults);
      if (request.offset() >= candidates.size()) {
        return new SearchPage(List.of(), candidates.size());
      }
      int end = Math.min(candidates.size(), request.offset() + request.limit());
      List<ResultOrdering.Candidate> pageCandidates = candidates.subList(request.offset(), end);

      PageMatches matches = partition(pageCandidates);
      Map<UUID, SearchClient> clientsById =
          clients.findByIds(matches.clientIds()).stream()
              .collect(Collectors.toMap(SearchClient::id, Function.identity()));
      Map<UUID, HydratedDocument> documentsById =
          documents.findByMatches(matches.documentMatches()).stream()
              .collect(Collectors.toMap(result -> result.document().id(), Function.identity()));

      List<SearchResult> page =
          pageCandidates.stream()
              .map(candidate -> toResult(candidate, clientsById, documentsById))
              .toList();
      returned = page.size();
      return new SearchPage(page, candidates.size());
    } finally {
      long totalNanos = System.nanoTime() - totalStartNanos;
      totalTimer.record(totalNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
      log.info(
          "Search audit request_id={} query_length={} lexical_hits={} semantic_hits={} returned={}"
              + " lexical_ms={} embed_query_ms={} semantic_ms={} total_ms={}",
          MDC.get("request_id"),
          request.query().length(),
          lexicalHits,
          semanticHits,
          returned,
          millis(timings.lexicalNanos()),
          millis(timings.queryEmbeddingNanos()),
          millis(timings.semanticNanos()),
          millis(totalNanos));
    }
  }

  private static <T> T time(Timer timer, LongConsumer recordNanos, Supplier<T> operation) {
    long startNanos = System.nanoTime();
    try {
      return operation.get();
    } finally {
      long elapsedNanos = System.nanoTime() - startNanos;
      timer.record(elapsedNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
      recordNanos.accept(elapsedNanos);
    }
  }

  private static long millis(long durationNanos) {
    return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(durationNanos);
  }

  private static PageMatches partition(List<ResultOrdering.Candidate> candidates) {
    List<UUID> clientIds = new java.util.ArrayList<>();
    List<DocumentMatch> documentMatches = new java.util.ArrayList<>();
    for (ResultOrdering.Candidate candidate : candidates) {
      switch (candidate) {
        case ResultOrdering.ClientCandidate(ClientMatch match) -> clientIds.add(match.clientId());
        case ResultOrdering.DocumentCandidate(DocumentMatch match) -> documentMatches.add(match);
      }
    }
    return new PageMatches(clientIds, documentMatches);
  }

  private static SearchResult toResult(
      ResultOrdering.Candidate candidate,
      Map<UUID, SearchClient> clientsById,
      Map<UUID, HydratedDocument> documentsById) {
    return switch (candidate) {
      case ResultOrdering.ClientCandidate(ClientMatch match) ->
          clientResult(match, clientsById.get(match.clientId()));
      case ResultOrdering.DocumentCandidate(DocumentMatch match) ->
          documentResult(match, documentsById.get(match.documentId()));
    };
  }

  private static SearchResult clientResult(ClientMatch match, SearchClient client) {
    BigDecimal score = BigDecimal.valueOf(match.score()).setScale(6, RoundingMode.HALF_UP);
    return new SearchResult("client", score, SearchMatch.field(match.field()), client, null);
  }

  private static SearchResult documentResult(DocumentMatch match, HydratedDocument document) {
    BigDecimal score = BigDecimal.valueOf(match.score()).setScale(6, RoundingMode.HALF_UP);
    return new SearchResult(
        "document", score, SearchMatch.passage(document.passage()), null, document.document());
  }

  @PreDestroy
  void closeSearchExecutor() {
    searchExecutor.close();
  }

  private static List<String> queryTokens(String query) {
    return java.util.Arrays.stream(query.split("\\s+"))
        .filter(token -> token.length() >= 3)
        .toList();
  }

  private record PageMatches(List<UUID> clientIds, List<DocumentMatch> documentMatches) {}

  private static final class SearchTimings {
    private volatile long lexicalNanos;
    private volatile long queryEmbeddingNanos;
    private volatile long semanticNanos;

    private void setLexicalNanos(long elapsedNanos) {
      lexicalNanos = elapsedNanos;
    }

    private void setQueryEmbeddingNanos(long elapsedNanos) {
      queryEmbeddingNanos = elapsedNanos;
    }

    private void setSemanticNanos(long elapsedNanos) {
      semanticNanos = elapsedNanos;
    }

    private long lexicalNanos() {
      return lexicalNanos;
    }

    private long queryEmbeddingNanos() {
      return queryEmbeddingNanos;
    }

    private long semanticNanos() {
      return semanticNanos;
    }
  }

  public record SearchPage(List<SearchResult> results, int total) {}
}
