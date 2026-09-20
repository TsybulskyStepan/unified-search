package com.example.searchapp.onboarding.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits document content into overlapping word windows (§5.3). Pure and stateless: it returns
 * code-point offsets into the caller's content, never a copy of the text, matching what {@code
 * document_chunk} stores (§3.1) and what the hydration query extracts by (§6.6).
 *
 * <p><strong>Geometry: 60-word windows on a 48-word stride</strong> (12-word / 20% overlap). This
 * remains within E5-base-v2's 512 word-piece input ceiling even for dense KYC prose (account
 * numbers, currency, dates). {@code EmbeddingWordPieceBoundTest} measures the real tokenizer over
 * the seed/eval corpus, including the title and E5's required passage prefix.
 */
public final class Chunker {
  static final int WINDOW_WORDS = 60;
  static final int STRIDE_WORDS = 48;

  private Chunker() {}

  /** Splits {@code content} into offset windows. Blank content yields no chunks. */
  public static List<Chunk> split(String content) {
    List<int[]> words = wordOffsets(content);
    if (words.isEmpty()) {
      return List.of();
    }

    List<Chunk> chunks = new ArrayList<>();
    int ordinal = 0;
    for (int start = 0; start < words.size(); start += STRIDE_WORDS) {
      int end = Math.min(start + WINDOW_WORDS, words.size());
      chunks.add(new Chunk(ordinal++, words.get(start)[0], words.get(end - 1)[1]));
      if (end == words.size()) {
        break;
      }
    }
    return chunks;
  }

  /**
   * The text to embed for a chunk: {@code title + "\n\n" + chunk text} (§5.3), so every chunk
   * carries its document's title.
   */
  public static String embeddingInput(String title, String content, Chunk chunk) {
    return title + "\n\n" + text(content, chunk);
  }

  /** Extracts the text a chunk covers, by code-point offset into {@code content}. */
  public static String text(String content, Chunk chunk) {
    int startIndex = content.offsetByCodePoints(0, chunk.startOffset());
    int endIndex = content.offsetByCodePoints(0, chunk.endOffset());
    return content.substring(startIndex, endIndex);
  }

  /**
   * Word boundaries as [startCodePoint, endCodePoint) pairs, tokenising on whitespace. Walks code
   * points rather than {@code char}s so a surrogate pair is never split.
   */
  private static List<int[]> wordOffsets(String content) {
    List<int[]> offsets = new ArrayList<>();
    int charIndex = 0;
    int codePointIndex = 0;
    int length = content.length();
    int wordStart = -1;
    while (charIndex < length) {
      int codePoint = content.codePointAt(charIndex);
      if (Character.isWhitespace(codePoint)) {
        if (wordStart >= 0) {
          offsets.add(new int[] {wordStart, codePointIndex});
          wordStart = -1;
        }
      } else if (wordStart < 0) {
        wordStart = codePointIndex;
      }
      charIndex += Character.charCount(codePoint);
      codePointIndex++;
    }
    if (wordStart >= 0) {
      offsets.add(new int[] {wordStart, codePointIndex});
    }
    return offsets;
  }
}
