package com.example.searchapp.search.service;

import com.example.searchapp.search.repository.model.DocumentMatch;
import java.util.List;
import java.util.Optional;

/**
 * The fused matches, best first. {@code queryVector} is present exactly when retrieval ran; it is
 * how {@link DocumentRetriever#hydrate} finds each match's passage.
 */
public record RetrievalResult(
    List<DocumentMatch> matches,
    Optional<float[]> queryVector,
    RetrievalMeasurements measurements) {
  static final RetrievalResult SKIPPED =
      new RetrievalResult(
          List.of(), Optional.empty(), new RetrievalMeasurements(0, 0, 0, 0, 0, 0, 0));

  public boolean ran() {
    return queryVector.isPresent();
  }
}
