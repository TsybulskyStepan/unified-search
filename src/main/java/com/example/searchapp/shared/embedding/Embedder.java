package com.example.searchapp.shared.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.onnx.AbstractInProcessEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.OnnxBertBiEncoder;
import dev.langchain4j.model.embedding.onnx.PoolingMode;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Wraps the in-process E5-base-v2 embedding model (§1.4). This is the only class that imports
 * LangChain4j (§1.4 invariant): everything downstream works with plain {@code float[]} vectors and
 * {@link #modelId()}, so a model swap touches this file alone.
 *
 * <p>The pinned ONNX model and tokenizer are checksum-verified at build time and packaged at the
 * classpath root — nothing downloads at runtime. Loading happens once, in this bean's field
 * initialiser, and {@link #warmUp()} runs a first inference at application startup so the very
 * first real request never pays that cost.
 */
@Component
public class Embedder {
  /**
   * Recorded against every chunk (schema §3.1) and filtered on at query time (§6.4), so a future
   * model change can never silently mix vector spaces.
   */
  public static final String MODEL_ID = "e5-base-v2";

  private static final String QUERY_PREFIX = "query: ";
  private static final String PASSAGE_PREFIX = "passage: ";

  private final E5BaseV2EmbeddingModel model = new E5BaseV2EmbeddingModel();

  @PostConstruct
  void warmUp() {
    embed("warm up");
  }

  /** The identifier stored on every chunk this instance produces. */
  public String modelId() {
    return MODEL_ID;
  }

  /** Embeds a single text, e.g. a search query (§6.4). */
  public float[] embed(String text) {
    return model.embed(QUERY_PREFIX + text).content().vector();
  }

  /** Embeds a batch of texts, e.g. a document's chunks (§5.2), in one call. */
  public List<float[]> embedAll(List<String> texts) {
    List<TextSegment> segments =
        texts.stream().map(text -> PASSAGE_PREFIX + text).map(TextSegment::from).toList();
    List<Embedding> embeddings = model.embedAll(segments).content();
    return embeddings.stream().map(Embedding::vector).toList();
  }

  /**
   * The exact word-piece count for {@code text}, read from the model's own tokenizer rather than
   * estimated from word count. Used to verify chunk geometry stays under the model's real input
   * limit (see {@code EmbeddingWordPieceBoundTest}); not used on any production path.
   */
  public int tokenCount(String text) {
    return model.embed(PASSAGE_PREFIX + text).tokenUsage().inputTokenCount();
  }

  private static final class E5BaseV2EmbeddingModel extends AbstractInProcessEmbeddingModel {
    private static final OnnxBertBiEncoder MODEL =
        loadFromJar("e5-base-v2.onnx", "e5-base-v2-tokenizer.json", PoolingMode.MEAN);

    private E5BaseV2EmbeddingModel() {
      super(null);
    }

    @Override
    protected OnnxBertBiEncoder model() {
      return MODEL;
    }

    @Override
    protected Integer knownDimension() {
      return 768;
    }
  }
}
