package com.example.searchapp.search.service;

import com.example.searchapp.search.repository.ClientMatch;
import com.example.searchapp.search.repository.ClientMention;
import com.example.searchapp.search.repository.DocumentMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class ResultOrdering {
  private ResultOrdering() {}

  static List<Candidate> order(
      List<ClientMatch> clients, List<DocumentMatch> documents, List<ClientMention> mentions) {
    if (mentions.size() != 1 || !mentions.getFirst().hasResidual()) {
      return defaultOrder(clients, documents);
    }

    ClientMention mention = mentions.getFirst();
    List<DocumentMatch> mentionedDocuments =
        documents.stream()
            .filter(document -> document.clientId().equals(mention.clientId()))
            .toList();
    if (mentionedDocuments.isEmpty()) {
      return defaultOrder(clients, documents);
    }

    List<Candidate> ordered = new ArrayList<>(clients.size() + documents.size() + 1);
    mentionedDocuments.forEach(document -> ordered.add(new DocumentCandidate(document)));
    ClientMatch mentionedClient =
        clients.stream()
            .filter(client -> client.clientId().equals(mention.clientId()))
            .findFirst()
            .orElse(new ClientMatch(mention.clientId(), mention.field(), mention.score()));
    ordered.add(new ClientCandidate(mentionedClient));
    clients.stream()
        .filter(client -> !client.clientId().equals(mention.clientId()))
        .forEach(client -> ordered.add(new ClientCandidate(client)));
    documents.stream()
        .filter(document -> !document.clientId().equals(mention.clientId()))
        .forEach(document -> ordered.add(new DocumentCandidate(document)));
    return ordered;
  }

  private static List<Candidate> defaultOrder(
      List<ClientMatch> clients, List<DocumentMatch> documents) {
    List<Candidate> ordered = new ArrayList<>(clients.size() + documents.size());
    clients.forEach(client -> ordered.add(new ClientCandidate(client)));
    documents.forEach(document -> ordered.add(new DocumentCandidate(document)));
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
