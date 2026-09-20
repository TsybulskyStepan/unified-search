package com.example.searchapp.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.searchapp.shared.embedding.Cosine;
import com.example.searchapp.shared.embedding.Embedder;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DocumentChunkSetTest {
  // Real ONNX model from the classpath, no Docker: the point is that each vector lines up with the
  // text it was made from, which only the real model can show.
  private static final Embedder EMBEDDER = new Embedder();
  private static final double SAME_TEXT = 0.999;

  private static final String TITLE = "Utility Bill";
  private static final String LABEL_TEXT = "utility bill proof of address";
  private static final String CONTENT =
      IntStream.rangeClosed(1, 90).mapToObj(i -> "word" + i).collect(Collectors.joining(" "));

  @Test
  void embedsTheLabelAndEveryBodyChunkInOrder() {
    DocumentChunkSet chunkSet = DocumentChunkSet.embed(EMBEDDER, TITLE, CONTENT, LABEL_TEXT);

    List<Chunk> expected = Chunker.split(CONTENT);
    assertThat(expected).hasSizeGreaterThan(1);
    assertThat(chunkSet.bodyChunks())
        .extracting(EmbeddedChunk::chunk)
        .containsExactlyElementsOf(expected);
    assertThat(chunkSet.labelEmbedding()).hasSize(384);
    assertThat(chunkSet.bodyChunks())
        .allSatisfy(chunk -> assertThat(chunk.embedding()).hasSize(384));
  }

  @Test
  void keepsTheLabelEmbeddingSeparateFromTheBodyEmbeddings() {
    DocumentChunkSet chunkSet = DocumentChunkSet.embed(EMBEDDER, TITLE, CONTENT, LABEL_TEXT);

    float[] label = EMBEDDER.embed(Chunker.labelEmbeddingInput(TITLE, LABEL_TEXT));
    assertThat(Cosine.between(chunkSet.labelEmbedding(), label)).isGreaterThan(SAME_TEXT);
  }

  @Test
  void pairsEachBodyChunkWithTheEmbeddingOfItsOwnText() {
    DocumentChunkSet chunkSet = DocumentChunkSet.embed(EMBEDDER, TITLE, CONTENT, LABEL_TEXT);

    for (EmbeddedChunk embedded : chunkSet.bodyChunks()) {
      float[] own = EMBEDDER.embed(Chunker.embeddingInput(TITLE, CONTENT, embedded.chunk()));
      assertThat(Cosine.between(embedded.embedding(), own)).isGreaterThan(SAME_TEXT);
    }
  }

  @Test
  void labelsADocumentWithNoLabelTextByItsTitleAlone() {
    DocumentChunkSet chunkSet = DocumentChunkSet.embed(EMBEDDER, TITLE, CONTENT, "");

    assertThat(Cosine.between(chunkSet.labelEmbedding(), EMBEDDER.embed(TITLE)))
        .isGreaterThan(SAME_TEXT);
  }

  @Test
  void embedsJustTheLabelWhenOnlyTheLabelChanged() {
    float[] label = DocumentChunkSet.embedLabel(EMBEDDER, TITLE, LABEL_TEXT);

    DocumentChunkSet chunkSet = DocumentChunkSet.embed(EMBEDDER, TITLE, CONTENT, LABEL_TEXT);
    assertThat(Cosine.between(label, chunkSet.labelEmbedding())).isGreaterThan(SAME_TEXT);
  }

  @Test
  void failsLoudlyWhenTheContentYieldsNoChunks() {
    assertThatThrownBy(() -> DocumentChunkSet.embed(EMBEDDER, TITLE, "   ", LABEL_TEXT))
        .isInstanceOf(IllegalStateException.class);
  }
}
