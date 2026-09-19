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
  private final DocumentSearchRepository documents;
  private final Embedder embedder;
  private final ExecutorService searchExecutor = Executors.newVirtualThreadPerTaskExecutor();

  public SearchService(
      ClientSearchRepository clients, DocumentSearchRepository documents, Embedder embedder) {
    this.clients = clients;
    this.documents = documents;
    this.embedder = embedder;
  }

  public SearchPage search(SearchRequest request) {
    List<String> queryTokens = queryTokens(request.query());
    CompletableFuture<List<ClientMatch>> clientMatches =
        CompletableFuture.supplyAsync(() -> clients.findMatches(request.query()), searchExecutor);
    CompletableFuture<List<ClientMention>> clientMentions =
        CompletableFuture.supplyAsync(() -> clients.findMentions(queryTokens), searchExecutor);
    CompletableFuture<List<DocumentMatch>> documentMatches =
        CompletableFuture.supplyAsync(
            () -> documents.findMatches(embedder.embed(request.query()), embedder.modelId()),
            searchExecutor);

    CompletableFuture.allOf(clientMatches, clientMentions, documentMatches).join();
    List<ClientMatch> clientResults = clientMatches.join();
    List<ClientMention> mentionResults = clientMentions.join();
    List<DocumentMatch> documentResults = documentMatches.join();

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
    return new SearchPage(page, candidates.size());
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

  public record SearchPage(List<SearchResult> results, int total) {}
}
