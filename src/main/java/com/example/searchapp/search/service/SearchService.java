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
import com.example.searchapp.search.repository.HydratedDocument;
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
import java.util.concurrent.TimeUnit;
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
  private final DocumentRetriever retriever;
  private final QueryPlanner queryPlanner;
  private final Timer planTimer;
  private final Timer clientTimer;
  private final Timer labelTimer;
  private final Timer lexicalTimer;
  private final Timer queryEmbeddingTimer;
  private final Timer semanticTimer;
  private final Timer totalTimer;
  private final ExecutorService searchExecutor = Executors.newVirtualThreadPerTaskExecutor();

  public SearchService(
      ClientSearchRepository clients,
      DocumentRetriever retriever,
      QueryPlanner queryPlanner,
      MeterRegistry meterRegistry) {
    this.clients = clients;
    this.retriever = retriever;
    this.queryPlanner = queryPlanner;
    planTimer = meterRegistry.timer("search.plan");
    clientTimer = meterRegistry.timer("search.clients");
    labelTimer = meterRegistry.timer("search.label");
    lexicalTimer = meterRegistry.timer("search.lexical");
    queryEmbeddingTimer = meterRegistry.timer("search.embed_query");
    semanticTimer = meterRegistry.timer("search.semantic");
    totalTimer = meterRegistry.timer("search.total");
  }

  public SearchPage search(SearchRequest request) {
    long totalStartNanos = System.nanoTime();
    SearchTimings timings = new SearchTimings();
    SearchHits hits = new SearchHits();
    String planShape = "document";
    int intentCount = 0;
    boolean mentionPresent = false;
    int returned = 0;
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
      mentionPresent = !plan.mentions().isEmpty();
      intentCount = plan.types().size() + plan.purposes().size();
      planShape = mentionPresent ? (plan.hasResidual() ? "compound" : "identity") : "document";

      CompletableFuture<List<ClientMatch>> clientMatches =
          CompletableFuture.supplyAsync(
              () ->
                  time(
                      clientTimer,
                      timings::setClientNanos,
                      () -> clients.findMatches(plan.query())),
              searchExecutor);
      // The client query must already be running when retrieve() blocks, so the two overlap and
      // latency stays the slower of them rather than their sum.
      DocumentRetriever.Result retrieval = retriever.retrieve(plan);
      if (retrieval.ran()) {
        hits = recordRetrieval(retrieval.measurements(), timings);
      }

      List<ClientMatch> clientResults = clientMatches.join();
      hits = hits.withClients(clientResults.size());
      List<ResultOrdering.Candidate> candidates =
          ResultOrdering.order(clientResults, retrieval.matches(), plan);
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
          retriever.hydrate(matches.documentMatches(), retrieval);
      List<SearchResult> page =
          pageCandidates.stream()
              .map(candidate -> toResult(candidate, clientsById, documentsById))
              .toList();
      returned = page.size();
      return new SearchPage(page, candidates.size());
    } finally {
      long totalNanos = System.nanoTime() - totalStartNanos;
      totalTimer.record(totalNanos, TimeUnit.NANOSECONDS);
      log.info(
          "Search audit request_id={} query_length={} plan_shape={} intent_count={}"
              + " mention_present={} client_hits={} label_hits={} lexical_hits={} semantic_hits={}"
              + " returned={} plan_ms={} clients_ms={} label_ms={} lexical_ms={} embed_query_ms={} semantic_ms={} total_ms={}",
          MDC.get("request_id"),
          request.query().length(),
          planShape,
          intentCount,
          mentionPresent,
          hits.clients(),
          hits.labels(),
          hits.lexical(),
          hits.semantic(),
          returned,
          millis(timings.planNanos()),
          millis(timings.clientNanos()),
          millis(timings.labelNanos()),
          millis(timings.lexicalNanos()),
          millis(timings.queryEmbeddingNanos()),
          millis(timings.semanticNanos()),
          millis(totalNanos));
    }
  }

  private SearchHits recordRetrieval(
      DocumentRetriever.Measurements measured, SearchTimings timings) {
    labelTimer.record(measured.labelNanos(), TimeUnit.NANOSECONDS);
    lexicalTimer.record(measured.lexicalNanos(), TimeUnit.NANOSECONDS);
    queryEmbeddingTimer.record(measured.queryEmbeddingNanos(), TimeUnit.NANOSECONDS);
    semanticTimer.record(measured.semanticNanos(), TimeUnit.NANOSECONDS);
    timings.setLabelNanos(measured.labelNanos());
    timings.setLexicalNanos(measured.lexicalNanos());
    timings.setQueryEmbeddingNanos(measured.queryEmbeddingNanos());
    timings.setSemanticNanos(measured.semanticNanos());
    return new SearchHits(0, measured.labelHits(), measured.lexicalHits(), measured.semanticHits());
  }

  private static <T> T time(Timer timer, LongConsumer recordNanos, Supplier<T> operation) {
    long startNanos = System.nanoTime();
    try {
      return operation.get();
    } finally {
      long elapsedNanos = System.nanoTime() - startNanos;
      timer.record(elapsedNanos, TimeUnit.NANOSECONDS);
      recordNanos.accept(elapsedNanos);
    }
  }

  private static long millis(long durationNanos) {
    return TimeUnit.NANOSECONDS.toMillis(durationNanos);
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
    return new SearchResult(
        "client", score, SearchMatch.field(match.field(), match.tier()), client, null);
  }

  private static SearchResult documentResult(DocumentMatch match, HydratedDocument document) {
    BigDecimal score = BigDecimal.valueOf(match.score()).setScale(6, RoundingMode.HALF_UP);
    return new SearchResult(
        "document",
        score,
        SearchMatch.passage(document.passage(), match.signals(), match.labels()),
        null,
        document.document());
  }

  @PreDestroy
  void closeSearchExecutor() {
    searchExecutor.close();
  }

  private record PageMatches(List<UUID> clientIds, List<DocumentMatch> documentMatches) {}

  private record SearchHits(int clients, int labels, int lexical, int semantic) {
    private SearchHits() {
      this(0, 0, 0, 0);
    }

    private SearchHits withClients(int clients) {
      return new SearchHits(clients, labels, lexical, semantic);
    }
  }

  private static final class SearchTimings {
    private volatile long planNanos;
    private volatile long clientNanos;
    private volatile long labelNanos;
    private volatile long lexicalNanos;
    private volatile long queryEmbeddingNanos;
    private volatile long semanticNanos;

    private void setPlanNanos(long value) {
      planNanos = value;
    }

    private void setClientNanos(long value) {
      clientNanos = value;
    }

    private void setLabelNanos(long value) {
      labelNanos = value;
    }

    private void setLexicalNanos(long value) {
      lexicalNanos = value;
    }

    private void setQueryEmbeddingNanos(long value) {
      queryEmbeddingNanos = value;
    }

    private void setSemanticNanos(long value) {
      semanticNanos = value;
    }

    private long planNanos() {
      return planNanos;
    }

    private long clientNanos() {
      return clientNanos;
    }

    private long labelNanos() {
      return labelNanos;
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
