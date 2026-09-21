package com.example.searchapp.search.service;

import com.example.searchapp.search.planner.ClientMention;
import com.example.searchapp.search.planner.QueryPlan;
import com.example.searchapp.search.repository.model.ClientMatch;
import com.example.searchapp.search.repository.model.DocumentMatch;
import com.example.searchapp.search.repository.model.MatchTier;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class ResultOrdering {
  private ResultOrdering() {}

  /**
   * Without a named client: identity clients, then documents, then context clients. With one: that
   * client's documents and the client itself, then everything else in the same order as above.
   */
  static List<Candidate> order(
      List<ClientMatch> clients, List<DocumentMatch> documents, QueryPlan plan) {
    if (plan.mentions().isEmpty()) {
      return concat(
          clientCandidates(clients, MatchTier.IDENTITY),
          documentCandidates(documents),
          clientCandidates(clients, MatchTier.CONTEXT));
    }

    Set<UUID> mentionedIds =
        plan.mentions().stream().map(ClientMention::clientId).collect(Collectors.toSet());
    Map<Boolean, List<DocumentMatch>> documentsByMention =
        documents.stream()
            .collect(
                Collectors.partitioningBy(document -> mentionedIds.contains(document.clientId())));
    List<ClientMatch> otherClients =
        clients.stream().filter(client -> !mentionedIds.contains(client.clientId())).toList();
    return concat(
        documentCandidates(documentsByMention.get(true)),
        mentionedClientCandidates(plan.mentions(), clients),
        documentCandidates(documentsByMention.get(false)),
        clientCandidates(otherClients, MatchTier.IDENTITY),
        clientCandidates(otherClients, MatchTier.CONTEXT));
  }

  /** A mentioned client keeps its own match when the lexical query found it, else the mention's. */
  private static List<Candidate> mentionedClientCandidates(
      List<ClientMention> mentions, List<ClientMatch> clients) {
    return mentions.stream()
        .<Candidate>map(
            mention ->
                new ClientCandidate(
                    clients.stream()
                        .filter(client -> client.clientId().equals(mention.clientId()))
                        .findFirst()
                        .orElseGet(
                            () ->
                                new ClientMatch(
                                    mention.clientId(),
                                    mention.field(),
                                    MatchTier.IDENTITY,
                                    mention.score()))))
        .toList();
  }

  private static List<Candidate> clientCandidates(List<ClientMatch> clients, MatchTier tier) {
    return clients.stream()
        .filter(client -> client.tier() == tier)
        .<Candidate>map(ClientCandidate::new)
        .toList();
  }

  private static List<Candidate> documentCandidates(List<DocumentMatch> documents) {
    return documents.stream().<Candidate>map(DocumentCandidate::new).toList();
  }

  @SafeVarargs
  private static List<Candidate> concat(List<Candidate>... parts) {
    return Stream.of(parts).flatMap(List::stream).toList();
  }

  sealed interface Candidate permits ClientCandidate, DocumentCandidate {
    UUID id();
  }

  record ClientCandidate(ClientMatch match) implements Candidate {
    @Override
    public UUID id() {
      return match.clientId();
    }
  }

  record DocumentCandidate(DocumentMatch match) implements Candidate {
    @Override
    public UUID id() {
      return match.documentId();
    }
  }
}
