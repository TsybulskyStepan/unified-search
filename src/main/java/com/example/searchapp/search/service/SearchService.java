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
import com.example.searchapp.search.service.SearchTelemetry.Stage;
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
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SearchService {
  private final ClientSearchRepository clients;
  private final DocumentRetriever retriever;
  private final QueryPlanner queryPlanner;
  private final SearchTelemetry telemetry;
  private final ExecutorService searchExecutor = Executors.newVirtualThreadPerTaskExecutor();

  public SearchService(
      ClientSearchRepository clients,
      DocumentRetriever retriever,
      QueryPlanner queryPlanner,
      SearchTelemetry telemetry) {
    this.clients = clients;
    this.retriever = retriever;
    this.queryPlanner = queryPlanner;
    this.telemetry = telemetry;
  }

  public SearchPage search(SearchRequest request) {
    SearchTelemetry.Recording recording = telemetry.start();
    int returned = 0;
    try {
      QueryPlan plan =
          recording.timed(
              Stage.PLAN,
              () -> {
                NormalizedQuery normalizedQuery = queryPlanner.normalize(request.query());
                List<MentionCandidate> mentionCandidates =
                    clients.findMentionCandidates(
                        normalizedQuery.tokens().stream().map(NormalizedToken::text).toList());
                return queryPlanner.plan(normalizedQuery, mentionCandidates);
              });
      recording.planned(plan);

      CompletableFuture<List<ClientMatch>> clientMatches =
          CompletableFuture.supplyAsync(
              () -> recording.timed(Stage.CLIENTS, () -> clients.findMatches(plan.query())),
              searchExecutor);
      // The client query must already be running when retrieve() blocks, so the two overlap and
      // latency stays the slower of them rather than their sum.
      DocumentRetriever.Result retrieval = retriever.retrieve(plan);
      if (retrieval.ran()) {
        recording.retrieved(retrieval.measurements());
      }

      List<ClientMatch> clientResults = clientMatches.join();
      recording.clientHits(clientResults.size());
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
      recording.finish(request.query().length(), returned);
    }
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

  public record SearchPage(List<SearchResult> results, int total) {}
}
