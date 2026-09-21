package com.example.searchapp.onboarding.service;

import com.example.searchapp.onboarding.service.model.Chunk;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits document content into overlapping word windows (§5.3). Pure and stateless: it returns
 * code-point offsets into the caller's content, never a copy of the text, matching what {@code
 * document_chunk} stores (§3.1) and what the hydration query extracts by (§6.6).
 *
 * <p><strong>Geometry: 50-word windows on a 40-word stride</strong> (10-word / 20% overlap). This
 * is smaller than the 150/120 originally sized against a documented 256 word-piece limit. That
 * limit does not hold for the model actually shipped in {@code
 * langchain4j-embeddings-all-minilm-l6-v2}: its bundled tokenizer truncates silently around 126
 * word pieces, verified directly against the model's own tokenizer (see {@code
 * EmbeddingWordPieceBoundTest}), not the 256 figure generally quoted for all-MiniLM-L6-v2. Dense
 * KYC prose (account numbers, currency, dates) measured at up to ~2 word pieces per word, so 50
 * words plus a title comfortably stays under that measured ceiling for real corpus content, where
 * 150 words routinely would not.
 */
public final class Chunker {
  static final int WINDOW_WORDS = 50;
  static final int STRIDE_WORDS = 40;

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

  /**
   * The text to embed for a document's label chunk (§5.3): {@code title + "\n" + labelText}, or the
   * title alone when {@code labelText} is empty ({@code unknown} documents, §3.3) — there is no
   * label to append, and no reason to embed a trailing blank line.
   */
  public static String labelEmbeddingInput(String title, String labelText) {
    return labelText.isEmpty() ? title : title + "\n" + labelText;
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
