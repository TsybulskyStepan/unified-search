package com.example.searchapp.search.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One document's evidence while the label, lexical and semantic result lists are fused: which
 * signals found it, the labels that admitted it, and the reciprocal-rank score the ranked signals
 * add up to.
 */
public final class FusionCandidate {
  private final UUID documentId;
  private final UUID clientId;
  private final Instant createdAt;
  private final Set<String> signals = new LinkedHashSet<>();
  private final Set<String> labels = new LinkedHashSet<>();
  private boolean labelMatch;
  private double fusedScore;
  private Double semanticScore;

  public FusionCandidate(UUID documentId, UUID clientId, Instant createdAt) {
    this.documentId = documentId;
    this.clientId = clientId;
    this.createdAt = createdAt;
  }

  public void addLabels(List<String> matchedLabels) {
    labelMatch = true;
    labels.addAll(matchedLabels);
    signals.add("label");
  }

  public void addLexical(double reciprocalRank) {
    fusedScore += reciprocalRank;
    signals.add("lexical");
  }

  public void addSemantic(double reciprocalRank, double score) {
    fusedScore += reciprocalRank;
    signals.add("semantic");
    semanticScore = score;
  }

  public UUID documentId() {
    return documentId;
  }

  public UUID clientId() {
    return clientId;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public boolean labelMatch() {
    return labelMatch;
  }

  public double fusedScore() {
    return fusedScore;
  }

  public Double semanticScore() {
    return semanticScore;
  }

  public List<String> signals() {
    return new ArrayList<>(signals);
  }

  public List<String> labels() {
    return new ArrayList<>(labels);
  }
}
