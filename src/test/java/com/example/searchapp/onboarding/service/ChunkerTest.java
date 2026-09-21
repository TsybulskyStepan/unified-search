package com.example.searchapp.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.searchapp.onboarding.service.model.Chunk;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkerTest {

  @Test
  void blankContentYieldsNoChunks() {
    assertThat(Chunker.split("")).isEmpty();
    assertThat(Chunker.split("   \n\t  ")).isEmpty();
  }

  @Test
  void singleWordIsOneChunk() {
    List<Chunk> chunks = Chunker.split("hello");

    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0)).isEqualTo(new Chunk(0, 0, 5));
  }

  @Test
  void contentAtOrBelowTheWindowIsOneChunk() {
    String content = wordsOf(Chunker.WINDOW_WORDS);

    List<Chunk> chunks = Chunker.split(content);

    assertThat(chunks).hasSize(1);
    Chunk chunk = chunks.get(0);
    assertThat(chunk.ordinal()).isZero();
    assertThat(chunk.startOffset()).isZero();
    assertThat(chunk.endOffset()).isEqualTo(codePointLength(content));
  }

  @Test
  void contentOverTheWindowOverlapsByTheStrideGap() {
    // window + one extra word forces a second chunk
    String content = wordsOf(Chunker.WINDOW_WORDS + 1);

    List<Chunk> chunks = Chunker.split(content);

    assertThat(chunks).hasSize(2);
    Chunk first = chunks.get(0);
    Chunk second = chunks.get(1);
    assertThat(first.ordinal()).isZero();
    assertThat(second.ordinal()).isEqualTo(1);
    // the second chunk starts at the stride'th word, before the first chunk ends —
    // that gap is the overlap
    assertThat(second.startOffset()).isLessThan(first.endOffset());
    assertThat(second.endOffset()).isEqualTo(codePointLength(content));
    // the overlap is exactly window - stride words
    String overlapText = content.substring(second.startOffset(), first.endOffset());
    int overlapWords = overlapText.trim().isEmpty() ? 0 : overlapText.trim().split("\\s+").length;
    assertThat(overlapWords).isEqualTo(Chunker.WINDOW_WORDS - Chunker.STRIDE_WORDS);
  }

  @Test
  void offsetsTileTheWholeContentWithNoGaps() {
    String content = wordsOf(3 * Chunker.STRIDE_WORDS + 5);

    List<Chunk> chunks = Chunker.split(content);

    assertThat(chunks).hasSizeGreaterThan(1);
    assertThat(chunks.get(0).startOffset()).isZero();
    assertThat(chunks.get(chunks.size() - 1).endOffset()).isEqualTo(codePointLength(content));
    for (int i = 1; i < chunks.size(); i++) {
      // each chunk after the first starts strictly before the previous one ends (overlap),
      // and strictly after the previous one starts (real progress)
      assertThat(chunks.get(i).startOffset()).isGreaterThan(chunks.get(i - 1).startOffset());
    }
  }

  @Test
  void extractReturnsExactlyTheChunkText() {
    String content = "the quick brown fox jumps over the lazy dog";

    List<Chunk> chunks = Chunker.split(content);

    assertThat(chunks).hasSize(1);
    assertThat(Chunker.text(content, chunks.get(0))).isEqualTo(content);
  }

  @Test
  void embeddingInputPrefixesTheTitle() {
    String content = "the quick brown fox";

    List<Chunk> chunks = Chunker.split(content);

    assertThat(Chunker.embeddingInput("Utility Bill", content, chunks.get(0)))
        .isEqualTo("Utility Bill\n\nthe quick brown fox");
  }

  @Test
  void handlesSurrogatePairsWithoutSplittingACharacter() {
    // U+1F600 GRINNING FACE is a surrogate pair (two UTF-16 chars, one code point)
    String emojiWord = "😀-address";
    String content = "utility " + emojiWord + " bill";

    List<Chunk> chunks = Chunker.split(content);

    assertThat(chunks).hasSize(1);
    String extracted = Chunker.text(content, chunks.get(0));
    assertThat(extracted).isEqualTo(content);
    // offsets are code points, not UTF-16 chars: "utility " is 8 code points long
    Chunk chunk = chunks.get(0);
    assertThat(content.codePointCount(0, content.length()))
        .isEqualTo(chunk.endOffset() - chunk.startOffset());
  }

  @Test
  void collapsesRunsOfWhitespaceBetweenWords() {
    String content = "utility   bill\n\ndue   now";

    List<Chunk> chunks = Chunker.split(content);

    assertThat(chunks).hasSize(1);
    assertThat(Chunker.text(content, chunks.get(0))).isEqualTo("utility   bill\n\ndue   now");
    // the offsets span from the first word's start to the last word's end, whitespace included
    // inside that span, but no chunk boundary was created by the run of whitespace
  }

  private static String wordsOf(int count) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < count; i++) {
      sb.append("word");
      if (i < count - 1) {
        sb.append(' ');
      }
    }
    return sb.toString();
  }

  private static int codePointLength(String s) {
    return s.codePointCount(0, s.length());
  }
}
