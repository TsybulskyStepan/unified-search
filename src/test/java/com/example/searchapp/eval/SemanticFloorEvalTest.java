package com.example.searchapp.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.example.searchapp.onboarding.seed.DemoCorpus;
import com.example.searchapp.onboarding.service.Classification;
import com.example.searchapp.onboarding.service.DocumentChunkSet;
import com.example.searchapp.onboarding.service.DocumentClassifier;
import com.example.searchapp.shared.embedding.Cosine;
import com.example.searchapp.shared.embedding.Embedder;
import com.example.searchapp.shared.taxonomy.Taxonomy;
import com.example.searchapp.shared.taxonomy.TaxonomyLoader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * The risk gate this ticket exists for (§11.3): proves the embedding model bridges a KYC *category*
 * query to a document *artifact* — "proof of address" to a utility bill, with no shared words —
 * before anything is built on top of that assumption.
 *
 * <p>For every positive pair, scores the query against its own expected document's best chunk. For
 * every negative query, scores it against the best (highest-scoring, i.e. worst-case) chunk across
 * the *entire* corpus. Both body and label chunks are included because both can admit a document
 * through semantic retrieval. The semantic floor recorded in {@code application.yaml} is the
 * midpoint between the lowest positive score and the highest negative score; this test fails the
 * build if that gap ever closes, per the ticket's own gate: "if positives and negatives do not
 * separate, stop and change the model before continuing."
 *
 * <p>Positives are every expected document of a {@code first} or {@code all_within} query in {@code
 * queries.json}; negatives are its {@code none} queries (§11.3). The configured floor must equal
 * the measured midpoint, so a change to the corpus or the query set that moves the gap fails the
 * build until {@code application.yaml} is re-derived.
 *
 * <p>This uses raw similarity scores computed in Java; {@code
 * SearchRelevanceEvalApiIntegrationTest} exercises the floors end to end.
 */
class SemanticFloorEvalTest {
  private static final Embedder embedder = new Embedder();

  private static final double FLOOR_TOLERANCE = 0.0005;

  private record EmbeddedDocument(String clientEmail, String title, List<float[]> chunkVectors) {}

  @Test
  void positiveAndNegativeSimilaritiesDoNotOverlap() {
    DemoCorpus corpus = EvalCorpusLoader.corpus();
    EvalQueries queries = EvalCorpusLoader.queries();

    List<EmbeddedDocument> documents = embedCorpus(corpus);

    List<Double> positiveScores = new ArrayList<>();
    for (EvalQueries.EvalQuery query : queries.queries()) {
      float[] queryVector = embedder.embed(query.query());
      for (EvalQueries.Item item : queries.semanticPositives(query, corpus)) {
        EmbeddedDocument expected = findDocument(documents, item);
        double best = bestCosine(queryVector, expected.chunkVectors());
        positiveScores.add(best);
        System.out.printf(
            "positive '%s' -> '%s' (%s): %.4f%n",
            query.query(), item.title(), item.clientEmail(), best);
      }
    }

    List<Double> negativeScores = new ArrayList<>();
    for (String negative : queries.negatives()) {
      float[] queryVector = embedder.embed(negative);
      // worst case: the single closest chunk anywhere in the corpus
      EmbeddedDocument closest = null;
      double worstCase = Double.NEGATIVE_INFINITY;
      for (EmbeddedDocument d : documents) {
        double score = bestCosine(queryVector, d.chunkVectors());
        if (score > worstCase) {
          worstCase = score;
          closest = d;
        }
      }
      negativeScores.add(worstCase);
      System.out.printf(
          "negative '%s': %.4f (closest: '%s' / %s)%n",
          negative, worstCase, closest.title(), closest.clientEmail());
    }

    double lowestPositive =
        positiveScores.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
    double highestNegative =
        negativeScores.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
    double gap = lowestPositive - highestNegative;
    double midpoint = (lowestPositive + highestNegative) / 2;

    System.out.printf(
        "lowest positive=%.4f highest negative=%.4f gap=%.4f midpoint(semantic floor)=%.4f%n",
        lowestPositive, highestNegative, gap, midpoint);

    assertThat(gap)
        .as(
            "every positive score (%.4f lowest) must clear every negative score (%.4f highest);"
                + " if this closes, change the model before building on it",
            lowestPositive, highestNegative)
        .isPositive();
    assertThat(configuredSemanticFloor())
        .as(
            "application.yaml semantic-floor must be the measured midpoint %.4f;"
                + " re-derive it from this output",
            midpoint)
        .isCloseTo(midpoint, within(FLOOR_TOLERANCE));
  }

  @SuppressWarnings("unchecked")
  private static double configuredSemanticFloor() {
    try (InputStream in = SemanticFloorEvalTest.class.getResourceAsStream("/application.yaml")) {
      Map<String, Object> root = new Yaml().load(in);
      Map<String, Object> app = (Map<String, Object>) root.get("app");
      Map<String, Object> search = (Map<String, Object>) app.get("search");
      return ((Number) search.get("semantic-floor")).doubleValue();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static List<EmbeddedDocument> embedCorpus(DemoCorpus corpus) {
    Taxonomy taxonomy = TaxonomyLoader.load(TaxonomyLoader.TAXONOMY_RESOURCE);
    DocumentClassifier classifier = new DocumentClassifier(taxonomy);
    List<EmbeddedDocument> documents = new ArrayList<>();
    for (DemoCorpus.DemoClient client : corpus.clients()) {
      for (DemoCorpus.DemoDocument document : client.documents()) {
        Classification classification =
            classifier.classify(document.title(), document.content(), null, List.of());
        DocumentChunkSet chunkSet =
            DocumentChunkSet.embed(
                embedder, document.title(), document.content(), classification.labelText());
        List<float[]> vectors = new ArrayList<>();
        chunkSet.bodyChunks().forEach(chunk -> vectors.add(chunk.embedding()));
        vectors.add(chunkSet.labelEmbedding());
        documents.add(new EmbeddedDocument(client.email(), document.title(), vectors));
      }
    }
    return documents;
  }

  private static EmbeddedDocument findDocument(
      List<EmbeddedDocument> documents, EvalQueries.Item item) {
    return documents.stream()
        .filter(d -> d.clientEmail().equals(item.clientEmail()) && d.title().equals(item.title()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "expected item references a document not in the corpus: " + item));
  }

  private static double bestCosine(float[] query, List<float[]> chunkVectors) {
    return chunkVectors.stream().mapToDouble(v -> Cosine.between(query, v)).max().orElseThrow();
  }
}
