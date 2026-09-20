package com.example.searchapp.onboarding.service;

import com.example.searchapp.onboarding.repository.DocumentRepository;
import com.example.searchapp.onboarding.repository.StaleDocument;
import com.example.searchapp.shared.embedding.Embedder;
import com.example.searchapp.shared.taxonomy.Taxonomy;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Brings rows written under an older taxonomy version up to date, at startup (§3.4). The taxonomy
 * is expected to change over time — that's the point of keeping it in a file (ticket 14) — and a
 * document classified under version 1 must not sit in the index answering version 2's questions
 * with version 1's labels.
 *
 * <p>Runs before {@code DemoSeeder} ({@link Order} below beats its unordered default), so a freshly
 * seeded document is never immediately picked up as stale by this same startup. Runs as a plain
 * {@link ApplicationRunner}, after the embedded server has started accepting requests (Spring Boot
 * starts the server, then calls runners, then reports the application ready) — so {@code /health}
 * and every endpoint are already live while this runs, and a stale row stays searchable under its
 * old labels for however long its batch takes to reach it (the same "briefly stale, never absent"
 * contract as a re-index).
 *
 * <p>One transaction per batch (§2.1 invariant: this is the one internal write to an existing row,
 * and it only ever changes labels), so a crash or restart partway through leaves every row either
 * already refreshed or still correctly flagged stale for the next startup to pick up — safe to
 * interrupt by construction, not by any special-cased recovery logic.
 */
@Component
@Order(0)
public class Reclassifier implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(Reclassifier.class);
  private static final int BATCH_SIZE = 100;

  private final DocumentRepository documents;
  private final DocumentClassifier classifier;
  private final Embedder embedder;
  private final Taxonomy taxonomy;

  public Reclassifier(
      DocumentRepository documents,
      DocumentClassifier classifier,
      Embedder embedder,
      Taxonomy taxonomy) {
    this.documents = documents;
    this.classifier = classifier;
    this.embedder = embedder;
    this.taxonomy = taxonomy;
  }

  @Override
  public void run(ApplicationArguments args) {
    reclassifyStaleRows();
  }

  /** Package-visible for direct invocation from tests, without going through startup. */
  void reclassifyStaleRows() {
    int totalReclassified = 0;
    int batchSize;
    do {
      List<StaleDocument> batch = documents.findStale(taxonomy.version(), BATCH_SIZE);
      // Classification and embedding are pure, CPU-bound work with no database involved (§5.2's
      // "embed outside the transaction" shape, applied to a whole batch): compute every row's new
      // labels and label embedding first, then persist the batch in the one transaction below.
      List<ReclassifiedRow> reclassified =
          batch.stream().map(this::computeReclassification).toList();
      documents.reclassifyBatch(reclassified, embedder.modelId());
      batchSize = batch.size();
      totalReclassified += batchSize;
    } while (batchSize == BATCH_SIZE);
    log.info(
        "Reclassification complete taxonomy_version={} rows_reclassified={}",
        taxonomy.version(),
        totalReclassified);
  }

  private ReclassifiedRow computeReclassification(StaleDocument row) {
    Classification classification =
        Classification.SOURCE_REQUEST.equals(row.classificationSource())
            ? classifier.relabel(row.documentType(), row.purposes(), row.classificationSource())
            : classifier.classify(row.title(), row.content(), null, List.of());
    float[] labelEmbedding =
        embedder.embed(Chunker.labelEmbeddingInput(row.title(), classification.labelText()));
    return new ReclassifiedRow(row.id(), classification, labelEmbedding);
  }
}
