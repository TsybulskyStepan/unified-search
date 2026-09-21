package com.example.searchapp.search.service;

import com.example.searchapp.search.entity.FusionCandidate;
import com.example.searchapp.search.repository.DocumentMatch;
import com.example.searchapp.search.repository.LabelDocumentMatch;
import com.example.searchapp.search.repository.RankedDocumentMatch;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class DocumentFusion {
  private static final int RRF_K = 60;

  private static final Comparator<FusionCandidate> BEST_FIRST =
      Comparator.comparing(FusionCandidate::labelMatch)
          .reversed()
          .thenComparing(FusionCandidate::fusedScore, Comparator.reverseOrder())
          .thenComparing(
              FusionCandidate::semanticScore, Comparator.nullsLast(Comparator.reverseOrder()))
          .thenComparing(FusionCandidate::createdAt, Comparator.reverseOrder())
          .thenComparing(FusionCandidate::documentId);

  private DocumentFusion() {}

  static List<DocumentMatch> fuse(
      List<LabelDocumentMatch> labels,
      List<RankedDocumentMatch> lexical,
      List<RankedDocumentMatch> semantic) {
    Map<UUID, FusionCandidate> candidates = new LinkedHashMap<>();
    for (LabelDocumentMatch match : labels) {
      FusionCandidate candidate =
          candidateFor(candidates, match.documentId(), match.clientId(), match.createdAt());
      candidates.put(match.documentId(), candidate.withLabels(match.labels()));
    }
    for (int rank = 0; rank < lexical.size(); rank++) {
      RankedDocumentMatch match = lexical.get(rank);
      FusionCandidate candidate =
          candidateFor(candidates, match.documentId(), match.clientId(), match.createdAt());
      candidates.put(match.documentId(), candidate.withLexical(reciprocalRank(rank)));
    }
    for (int rank = 0; rank < semantic.size(); rank++) {
      RankedDocumentMatch match = semantic.get(rank);
      FusionCandidate candidate =
          candidateFor(candidates, match.documentId(), match.clientId(), match.createdAt());
      candidates.put(
          match.documentId(), candidate.withSemantic(reciprocalRank(rank), match.score()));
    }

    return candidates.values().stream().sorted(BEST_FIRST).map(DocumentFusion::toMatch).toList();
  }

  private static FusionCandidate candidateFor(
      Map<UUID, FusionCandidate> candidates, UUID documentId, UUID clientId, Instant createdAt) {
    return candidates.getOrDefault(documentId, FusionCandidate.of(documentId, clientId, createdAt));
  }

  private static double reciprocalRank(int zeroBasedRank) {
    return 1.0 / (RRF_K + zeroBasedRank + 1);
  }

  private static DocumentMatch toMatch(FusionCandidate candidate) {
    return new DocumentMatch(
        candidate.documentId(),
        candidate.clientId(),
        candidate.fusedScore(),
        candidate.signals(),
        candidate.labels());
  }
}
