package com.example.searchapp.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.searchapp.onboarding.seed.DemoCorpus;
import com.example.searchapp.onboarding.service.Chunk;
import com.example.searchapp.onboarding.service.Chunker;
import com.example.searchapp.onboarding.service.Classification;
import com.example.searchapp.onboarding.service.DocumentClassifier;
import com.example.searchapp.shared.embedding.Cosine;
import com.example.searchapp.shared.embedding.Embedder;
import com.example.searchapp.shared.taxonomy.Taxonomy;
import com.example.searchapp.shared.taxonomy.TaxonomyLoader;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The risk gate this ticket exists for (§12.3): proves the embedding model bridges a KYC *category*
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
 * <p>This uses raw similarity scores computed in Java, not the search endpoint (that version,
 * exercising the real floors end to end, is ticket 08).
 */
class SemanticFloorEvalTest {
  private static final Embedder embedder = new Embedder();

  private record EmbeddedDocument(String clientEmail, String title, List<float[]> chunkVectors) {}

  @Test
  void positiveAndNegativeSimilaritiesDoNotOverlap() {
    DemoCorpus corpus = EvalCorpusLoader.corpus();
    EvalQueries queries = EvalCorpusLoader.queries();

    List<EmbeddedDocument> documents = embedCorpus(corpus);

    List<Double> positiveScores = new ArrayList<>();
    for (EvalQueries.PositivePair pair : queries.positives()) {
      float[] queryVector = embedder.embed(pair.query());
      EmbeddedDocument expected = findDocument(documents, pair);
      double best = bestCosine(queryVector, expected.chunkVectors());
      positiveScores.add(best);
      System.out.printf(
          "positive '%s' -> '%s' (%s): %.4f%n",
          pair.query(), pair.documentTitle(), pair.clientEmail(), best);
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
  }

  private static List<EmbeddedDocument> embedCorpus(DemoCorpus corpus) {
    Taxonomy taxonomy = TaxonomyLoader.load(TaxonomyLoader.TAXONOMY_RESOURCE);
    DocumentClassifier classifier = new DocumentClassifier(taxonomy);
    List<EmbeddedDocument> documents = new ArrayList<>();
    for (DemoCorpus.DemoClient client : corpus.clients()) {
      for (DemoCorpus.DemoDocument document : client.documents()) {
        List<Chunk> chunks = Chunker.split(document.content());
        List<String> embeddingInputs =
            chunks.stream()
                .map(chunk -> Chunker.embeddingInput(document.title(), document.content(), chunk))
                .toList();
        Classification classification =
            classifier.classify(document.title(), document.content(), null, List.of());
        embeddingInputs = new ArrayList<>(embeddingInputs);
        embeddingInputs.add(
            Chunker.labelEmbeddingInput(document.title(), classification.labelText()));
        List<float[]> vectors = embedder.embedAll(embeddingInputs);
        documents.add(new EmbeddedDocument(client.email(), document.title(), vectors));
      }
    }
    return documents;
  }

  private static EmbeddedDocument findDocument(
      List<EmbeddedDocument> documents, EvalQueries.PositivePair pair) {
    return documents.stream()
        .filter(
            d ->
                d.clientEmail().equals(pair.clientEmail())
                    && d.title().equals(pair.documentTitle()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "positive pair references a document not in the corpus: " + pair));
  }

  private static double bestCosine(float[] query, List<float[]> chunkVectors) {
    return chunkVectors.stream().mapToDouble(v -> Cosine.between(query, v)).max().orElseThrow();
  }
}
