package com.example.searchapp.search.entity;

import com.example.searchapp.search.repository.model.Signal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * One document's evidence while the label, lexical and semantic result lists are fused: which
 * signals found it, the labels that admitted it, and the reciprocal-rank score the ranked signals
 * add up to. Immutable; each {@code with...} returns the candidate with that evidence added.
 */
public record FusionCandidate(
    UUID documentId,
    UUID clientId,
    Instant createdAt,
    List<String> labels,
    List<Signal> signals,
    double fusedScore,
    Double semanticScore) {

  public static FusionCandidate of(UUID documentId, UUID clientId, Instant createdAt) {
    return new FusionCandidate(documentId, clientId, createdAt, List.of(), List.of(), 0, null);
  }

  public boolean labelMatch() {
    return signals.contains(Signal.LABEL);
  }

  public FusionCandidate withLabels(List<String> matchedLabels) {
    return new FusionCandidate(
        documentId,
        clientId,
        createdAt,
        union(labels, matchedLabels),
        union(signals, List.of(Signal.LABEL)),
        fusedScore,
        semanticScore);
  }

  public FusionCandidate withLexical(double reciprocalRank) {
    return new FusionCandidate(
        documentId,
        clientId,
        createdAt,
        labels,
        union(signals, List.of(Signal.LEXICAL)),
        fusedScore + reciprocalRank,
        semanticScore);
  }

  public FusionCandidate withSemantic(double reciprocalRank, double score) {
    return new FusionCandidate(
        documentId,
        clientId,
        createdAt,
        labels,
        union(signals, List.of(Signal.SEMANTIC)),
        fusedScore + reciprocalRank,
        score);
  }

  @SuppressWarnings("unchecked")
  private static <T> List<T> union(List<T> existing, List<T> added) {
    return Stream.concat(existing.stream(), added.stream()).distinct().toList();
  }
}
