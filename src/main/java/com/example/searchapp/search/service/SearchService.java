package com.example.searchapp.search.service;

import com.example.searchapp.search.dto.SearchMatch;
import com.example.searchapp.search.dto.SearchRequest;
import com.example.searchapp.search.dto.SearchResult;
import com.example.searchapp.search.entity.SearchClient;
import com.example.searchapp.search.repository.ClientMatch;
import com.example.searchapp.search.repository.ClientSearchRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SearchService {
  private final ClientSearchRepository clients;

  public SearchService(ClientSearchRepository clients) {
    this.clients = clients;
  }

  public SearchPage search(SearchRequest request) {
    List<ClientMatch> matches = clients.findMatches(request.query());
    if (request.offset() >= matches.size()) {
      return new SearchPage(List.of(), matches.size());
    }
    int end = Math.min(matches.size(), request.offset() + request.limit());
    List<ClientMatch> candidates = matches.subList(request.offset(), end);
    Map<java.util.UUID, SearchClient> clientsById =
        clients.findByIds(candidates.stream().map(ClientMatch::clientId).toList()).stream()
            .collect(Collectors.toMap(SearchClient::id, Function.identity()));
    List<SearchResult> page =
        candidates.stream()
            .map(match -> toResult(match, clientsById.get(match.clientId())))
            .toList();
    return new SearchPage(page, matches.size());
  }

  private static SearchResult toResult(ClientMatch match, SearchClient client) {
    BigDecimal score = BigDecimal.valueOf(match.score()).setScale(6, RoundingMode.HALF_UP);
    return new SearchResult("client", score, new SearchMatch(match.field()), client);
  }

  public record SearchPage(List<SearchResult> results, int total) {}
}
