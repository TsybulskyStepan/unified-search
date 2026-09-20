package com.example.searchapp.search.service;

import com.example.searchapp.search.repository.DocumentMatch;
import com.example.searchapp.search.repository.LabelDocumentMatch;
import com.example.searchapp.search.repository.RankedDocumentMatch;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class DocumentFusion {
  private static final int RRF_K = 60;

  private DocumentFusion() {}

  static List<DocumentMatch> fuse(
      List<LabelDocumentMatch> labels,
      List<RankedDocumentMatch> lexical,
      List<RankedDocumentMatch> semantic) {
    Map<UUID, Candidate> candidates = new java.util.LinkedHashMap<>();
    labels.forEach(
        match ->
            candidates.putIfAbsent(
                match.documentId(),
                new Candidate(match.documentId(), match.clientId(), match.createdAt())));
    lexical.forEach(
        match ->
            candidates.putIfAbsent(
                match.documentId(),
                new Candidate(match.documentId(), match.clientId(), match.createdAt())));
    semantic.forEach(
        match ->
            candidates.putIfAbsent(
                match.documentId(),
                new Candidate(match.documentId(), match.clientId(), match.createdAt())));

    labels.forEach(
        label -> {
          Candidate candidate = candidates.get(label.documentId());
          candidate.labelMatch = true;
          candidate.labels.addAll(label.labels());
          candidate.signals.add("label");
        });
    addRankedSignal(candidates, lexical, "lexical");
    addRankedSignal(candidates, semantic, "semantic");

    return candidates.values().stream()
        .sorted(
            Comparator.comparing(Candidate::labelMatch)
                .reversed()
                .thenComparing(Candidate::fusedScore, Comparator.reverseOrder())
                .thenComparing(
                    Candidate::semanticScore, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Candidate::createdAt, Comparator.reverseOrder())
                .thenComparing(Candidate::documentId))
        .map(Candidate::toMatch)
        .toList();
  }

  private static void addRankedSignal(
      Map<UUID, Candidate> candidates, List<RankedDocumentMatch> matches, String signal) {
    for (int index = 0; index < matches.size(); index++) {
      RankedDocumentMatch match = matches.get(index);
      Candidate candidate = candidates.get(match.documentId());
      candidate.fusedScore += 1.0 / (RRF_K + index + 1);
      candidate.signals.add(signal);
      if (signal.equals("semantic")) {
        candidate.semanticScore = match.score();
      }
    }
  }

  private static final class Candidate {
    private final UUID documentId;
    private final UUID clientId;
    private final Instant createdAt;
    private final LinkedHashSet<String> signals = new LinkedHashSet<>();
    private final LinkedHashSet<String> labels = new LinkedHashSet<>();
    private boolean labelMatch;
    private double fusedScore;
    private Double semanticScore;

    private Candidate(UUID documentId, UUID clientId, Instant createdAt) {
      this.documentId = documentId;
      this.clientId = clientId;
      this.createdAt = createdAt;
    }

    private UUID documentId() {
      return documentId;
    }

    private boolean labelMatch() {
      return labelMatch;
    }

    private Double fusedScore() {
      return fusedScore;
    }

    private Double semanticScore() {
      return semanticScore;
    }

    private Instant createdAt() {
      return createdAt;
    }

    private DocumentMatch toMatch() {
      return new DocumentMatch(
          documentId, clientId, fusedScore, new ArrayList<>(signals), new ArrayList<>(labels));
    }
  }
}
