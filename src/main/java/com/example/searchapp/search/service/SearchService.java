package com.example.searchapp.search.service;

import com.example.searchapp.search.dto.SearchMatch;
import com.example.searchapp.search.dto.SearchRequest;
import com.example.searchapp.search.dto.SearchResult;
import com.example.searchapp.search.entity.SearchClient;
import com.example.searchapp.search.planner.MentionCandidate;
import com.example.searchapp.search.planner.NormalizedQuery;
import com.example.searchapp.search.planner.NormalizedToken;
import com.example.searchapp.search.planner.QueryPlan;
import com.example.searchapp.search.planner.QueryPlanner;
import com.example.searchapp.search.repository.ClientMatch;
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
  private final QueryPlanner queryPlanner;
  private final Timer planTimer;
  private final Timer lexicalTimer;
  private final Timer queryEmbeddingTimer;
  private final Timer semanticTimer;
  private final Timer totalTimer;
  private final ExecutorService searchExecutor = Executors.newVirtualThreadPerTaskExecutor();

  public SearchService(
      ClientSearchRepository clients,
      DocumentSearchRepository documents,
      Embedder embedder,
      QueryPlanner queryPlanner,
      MeterRegistry meterRegistry) {
    this.clients = clients;
    this.documents = documents;
    this.embedder = embedder;
    this.queryPlanner = queryPlanner;
    planTimer = meterRegistry.timer("search.plan");
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
    String planShape = "document";
    int intentCount = 0;
    boolean mentionPresent = false;
    try {
      QueryPlan plan =
          time(
              planTimer,
              timings::setPlanNanos,
              () -> {
                NormalizedQuery normalizedQuery = queryPlanner.normalize(request.query());
                List<MentionCandidate> mentionCandidates =
                    clients.findMentionCandidates(
                        normalizedQuery.tokens().stream().map(NormalizedToken::text).toList());
                return queryPlanner.plan(normalizedQuery, mentionCandidates);
              });
      mentionPresent = plan.mention() != null;
      intentCount = plan.intents().size();
      planShape = mentionPresent ? (plan.hasResidual() ? "compound" : "identity") : "document";
      CompletableFuture<List<ClientMatch>> clientMatches =
          CompletableFuture.supplyAsync(
              () ->
                  time(
                      lexicalTimer,
                      timings::setLexicalNanos,
                      () -> clients.findMatches(plan.query())),
              searchExecutor);
      CompletableFuture<List<DocumentMatch>> documentMatches =
          CompletableFuture.supplyAsync(
              () -> {
                if (!plan.hasResidual()) {
                  return List.of();
                }
                float[] queryVector =
                    time(
                        queryEmbeddingTimer,
                        timings::setQueryEmbeddingNanos,
                        () -> embedder.embed(plan.residual()));
                return time(
                    semanticTimer,
                    timings::setSemanticNanos,
                    () -> documents.findMatches(queryVector, embedder.modelId()));
              },
              searchExecutor);

      CompletableFuture.allOf(clientMatches, documentMatches).join();
      List<ClientMatch> clientResults = clientMatches.join();
      List<DocumentMatch> documentResults = documentMatches.join();
      lexicalHits = clientResults.size();
      semanticHits = documentResults.size();

      List<ResultOrdering.Candidate> candidates =
          ResultOrdering.order(clientResults, documentResults, plan);
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
          "Search audit request_id={} query_length={} plan_shape={} intent_count={}"
              + " mention_present={} lexical_hits={} semantic_hits={} returned={}"
              + " plan_ms={} lexical_ms={} embed_query_ms={} semantic_ms={} total_ms={}",
          MDC.get("request_id"),
          request.query().length(),
          planShape,
          intentCount,
          mentionPresent,
          lexicalHits,
          semanticHits,
          returned,
          millis(timings.planNanos()),
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

  private record PageMatches(List<UUID> clientIds, List<DocumentMatch> documentMatches) {}

  private static final class SearchTimings {
    private volatile long planNanos;
    private volatile long lexicalNanos;
    private volatile long queryEmbeddingNanos;
    private volatile long semanticNanos;

    private void setPlanNanos(long elapsedNanos) {
      planNanos = elapsedNanos;
    }

    private void setLexicalNanos(long elapsedNanos) {
      lexicalNanos = elapsedNanos;
    }

    private void setQueryEmbeddingNanos(long elapsedNanos) {
      queryEmbeddingNanos = elapsedNanos;
    }

    private void setSemanticNanos(long elapsedNanos) {
      semanticNanos = elapsedNanos;
    }

    private long planNanos() {
      return planNanos;
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
