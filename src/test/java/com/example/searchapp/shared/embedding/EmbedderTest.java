package com.example.searchapp.shared.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmbedderTest {

  // Real ONNX model, loaded from the classpath (no network) — shared across tests in this class
  // so the ~seconds-long first load happens once.
  private static final Embedder embedder = new Embedder();

  @Test
  void exposesTheModelIdentifier() {
    assertThat(embedder.modelId()).isEqualTo("e5-base-v2");
  }

  @Test
  void warmUpLoadsTheModelAndRunsWithoutError() {
    embedder.warmUp();
  }

  @Test
  void embedsASingleTextTo768Dimensions() {
    float[] vector = embedder.embed("proof of address");

    assertThat(vector).hasSize(768);
  }

  @Test
  void embedsABatchInOneCall() {
    var vectors = embedder.embedAll(java.util.List.of("first chunk", "second chunk"));

    assertThat(vectors).hasSize(2);
    assertThat(vectors.get(0)).hasSize(768);
    assertThat(vectors.get(1)).hasSize(768);
  }

  @Test
  void tokenCountIsExactNotEstimated() {
    // ten distinct, common plain-English words plus E5's passage prefix and two special tokens
    int count =
        embedder.tokenCount(
            "account statement balance payment address utility gas water meter reading");

    assertThat(count).isEqualTo(12);
  }

  @Test
  void semanticallyRelatedTextsAreCloserThanUnrelatedOnes() {
    float[] utilityBillQuery = embedder.embed("proof of address");
    float[] utilityBillChunk =
        embedder.embed(
            "Account number 8827-4491 for the billing period, closing balance including VAT,"
                + " meter reading, supply address");
    float[] unrelated = embedder.embed("best pizza recipes in Naples");

    double related = Cosine.between(utilityBillQuery, utilityBillChunk);
    double notRelated = Cosine.between(utilityBillQuery, unrelated);

    assertThat(related).isGreaterThan(notRelated);
  }
}
