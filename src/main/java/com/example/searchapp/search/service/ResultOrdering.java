package com.example.searchapp.search.service;

import com.example.searchapp.search.planner.ClientMention;
import com.example.searchapp.search.planner.QueryPlan;
import com.example.searchapp.search.repository.ClientMatch;
import com.example.searchapp.search.repository.DocumentMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

final class ResultOrdering {
  private ResultOrdering() {}

  static List<Candidate> order(
      List<ClientMatch> clients, List<DocumentMatch> documents, QueryPlan plan) {
    if (plan.mentions().isEmpty()) {
      return defaultOrder(clients, documents);
    }

    Set<UUID> mentionedIds =
        plan.mentions().stream().map(ClientMention::clientId).collect(Collectors.toSet());
    List<Candidate> ordered =
        new ArrayList<>(clients.size() + documents.size() + mentionedIds.size());
    documents.stream()
        .filter(document -> mentionedIds.contains(document.clientId()))
        .forEach(document -> ordered.add(new DocumentCandidate(document)));
    for (ClientMention mention : plan.mentions()) {
      ClientMatch mentionedClient =
          clients.stream()
              .filter(client -> client.clientId().equals(mention.clientId()))
              .findFirst()
              .orElse(
                  new ClientMatch(
                      mention.clientId(), mention.field(), "identity", mention.score()));
      ordered.add(new ClientCandidate(mentionedClient));
    }
    documents.stream()
        .filter(document -> !mentionedIds.contains(document.clientId()))
        .forEach(document -> ordered.add(new DocumentCandidate(document)));
    clients.stream()
        .filter(
            client -> !mentionedIds.contains(client.clientId()) && client.tier().equals("identity"))
        .forEach(client -> ordered.add(new ClientCandidate(client)));
    clients.stream()
        .filter(
            client -> !mentionedIds.contains(client.clientId()) && client.tier().equals("context"))
        .forEach(client -> ordered.add(new ClientCandidate(client)));
    return ordered;
  }

  private static List<Candidate> defaultOrder(
      List<ClientMatch> clients, List<DocumentMatch> documents) {
    List<Candidate> ordered = new ArrayList<>(clients.size() + documents.size());
    clients.stream()
        .filter(client -> client.tier().equals("identity"))
        .forEach(client -> ordered.add(new ClientCandidate(client)));
    documents.forEach(document -> ordered.add(new DocumentCandidate(document)));
    clients.stream()
        .filter(client -> client.tier().equals("context"))
        .forEach(client -> ordered.add(new ClientCandidate(client)));
    return ordered;
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
