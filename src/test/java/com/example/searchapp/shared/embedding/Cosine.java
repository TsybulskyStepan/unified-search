package com.example.searchapp.shared.embedding;

/**
 * Cosine similarity over the plain {@code float[]} vectors {@link Embedder} returns. Test-only:
 * production code never compares vectors in Java — that's pgvector's {@code <=>} operator (§6.4).
 */
public final class Cosine {
  private Cosine() {}

  public static double between(float[] a, float[] b) {
    double dot = 0;
    double normA = 0;
    double normB = 0;
    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
      normA += a[i] * a[i];
      normB += b[i] * b[i];
    }
    return dot / (Math.sqrt(normA) * Math.sqrt(normB));
  }
}
