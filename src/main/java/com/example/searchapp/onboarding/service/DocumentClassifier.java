package com.example.searchapp.onboarding.service;

import com.example.searchapp.onboarding.service.model.Classification;
import com.example.searchapp.shared.taxonomy.DocumentType;
import com.example.searchapp.shared.taxonomy.Purpose;
import com.example.searchapp.shared.taxonomy.Taxonomy;
import com.example.searchapp.shared.web.RequestValidationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Classifies a document by rule, at the moment it is created — no model call, no credential, no
 * added latency on {@code POST} (§3.3, §1.4, §13). Deterministic: the same title and content always
 * score the same way against the same {@link Taxonomy}, so a reviewer with a clean clone and no API
 * key still gets a fully labelled corpus.
 *
 * <ol>
 *   <li>A request-supplied type is validated and used, source {@value
 *       Classification#SOURCE_REQUEST}.
 *   <li>Otherwise every type is scored: each title pattern found (case-insensitive substring on the
 *       title) is worth {@value #TITLE_PATTERN_SCORE}, each content pattern found is worth {@value
 *       #CONTENT_PATTERN_SCORE}. The highest-scoring type wins, source {@value
 *       Classification#SOURCE_RULE}. A best score below {@value #MIN_RULE_SCORE} (one title
 *       pattern, or two content patterns), or a tie for first place, yields {@link
 *       Taxonomy#UNKNOWN_TYPE}, source {@value Classification#SOURCE_UNKNOWN} — a guess would be
 *       worse than admitting the rules didn't recognise the document. One content word is not
 *       evidence: a letter of authority that mentions "beneficiaries" is not a trust deed.
 * </ol>
 */
@Component
public class DocumentClassifier {
  private static final int TITLE_PATTERN_SCORE = 2;
  private static final int CONTENT_PATTERN_SCORE = 1;
  private static final int MIN_RULE_SCORE = 2;

  private final Taxonomy taxonomy;

  public DocumentClassifier(Taxonomy taxonomy) {
    this.taxonomy = taxonomy;
  }

  /**
   * @param requestedType a taxonomy type id from the request, or {@code null} to classify by rule
   * @param requestedPurposes taxonomy purpose ids from the request; must be empty when {@code
   *     requestedType} is {@code null} (§4.2)
   */
  public Classification classify(
      String title, String content, String requestedType, List<String> requestedPurposes) {
    if (requestedType == null) {
      if (!requestedPurposes.isEmpty()) {
        throw new RequestValidationException(
            Map.of("purposes", "must not be set without document_type"));
      }
      return classifyByRule(title, content);
    }
    return classifyRequested(requestedType, requestedPurposes);
  }

  /**
   * Rebuilds {@code label_text} for a type and purposes already assigned, against the current
   * taxonomy's labels — used by {@code Reclassifier} (§3.4) for rows a request classified, whose
   * type is kept across a taxonomy version change. Falls back to {@link Taxonomy#UNKNOWN_TYPE} if
   * the stored type no longer exists in the current file.
   */
  public Classification relabel(String documentType, List<String> purposes, String source) {
    DocumentType type = taxonomy.types().get(documentType);
    if (type == null) {
      return unknownClassification();
    }
    return new Classification(
        type.id(), purposes, source, taxonomy.version(), labelText(type, purposes));
  }

  private Classification classifyRequested(String requestedType, List<String> requestedPurposes) {
    DocumentType type = taxonomy.types().get(requestedType);
    if (type == null) {
      throw new RequestValidationException(Map.of("document_type", "must be a taxonomy type id"));
    }

    List<String> purposes =
        requestedPurposes.isEmpty() ? type.defaultPurposes() : requestedPurposes;
    Map<String, String> errors = new LinkedHashMap<>();
    for (int i = 0; i < purposes.size(); i++) {
      if (!taxonomy.purposes().containsKey(purposes.get(i))) {
        errors.put("purposes[" + i + "]", "must be a taxonomy purpose id");
      }
    }
    if (!errors.isEmpty()) {
      throw new RequestValidationException(errors);
    }

    return new Classification(
        type.id(),
        purposes,
        Classification.SOURCE_REQUEST,
        taxonomy.version(),
        labelText(type, purposes));
  }

  private Classification classifyByRule(String title, String content) {
    String normalizedTitle = title.toLowerCase(Locale.ROOT);
    String normalizedContent = content.toLowerCase(Locale.ROOT);

    String bestTypeId = null;
    int bestScore = 0;
    int bestScoreCount = 0;
    for (DocumentType type : taxonomy.types().values()) {
      if (Taxonomy.UNKNOWN_TYPE.equals(type.id())) {
        continue;
      }
      int score = score(type, normalizedTitle, normalizedContent);
      if (score > bestScore) {
        bestScore = score;
        bestTypeId = type.id();
        bestScoreCount = 1;
      } else if (score > 0 && score == bestScore) {
        bestScoreCount++;
      }
    }

    if (bestScore < MIN_RULE_SCORE || bestScoreCount > 1) {
      return unknownClassification();
    }

    DocumentType type = taxonomy.types().get(bestTypeId);
    return new Classification(
        type.id(),
        type.defaultPurposes(),
        Classification.SOURCE_RULE,
        taxonomy.version(),
        labelText(type, type.defaultPurposes()));
  }

  private static int score(DocumentType type, String normalizedTitle, String normalizedContent) {
    int score = 0;
    for (String pattern : type.titlePatterns()) {
      if (normalizedTitle.contains(pattern.toLowerCase(Locale.ROOT))) {
        score += TITLE_PATTERN_SCORE;
      }
    }
    for (String pattern : type.contentPatterns()) {
      if (normalizedContent.contains(pattern.toLowerCase(Locale.ROOT))) {
        score += CONTENT_PATTERN_SCORE;
      }
    }
    return score;
  }

  /**
   * The type label followed by each purpose's label, space separated; empty for {@code unknown}.
   */
  private String labelText(DocumentType type, List<String> purposes) {
    if (Taxonomy.UNKNOWN_TYPE.equals(type.id())) {
      return "";
    }
    StringBuilder labelText = new StringBuilder(type.label());
    for (String purposeId : purposes) {
      Purpose purpose = taxonomy.purposes().get(purposeId);
      labelText.append(' ').append(purpose.label());
    }
    return labelText.toString();
  }

  private Classification unknownClassification() {
    return new Classification(
        Taxonomy.UNKNOWN_TYPE, List.of(), Classification.SOURCE_UNKNOWN, taxonomy.version(), "");
  }
}
