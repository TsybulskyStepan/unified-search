package com.example.searchapp.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.searchapp.eval.EvalCorpusLoader;
import com.example.searchapp.onboarding.seed.DemoCorpus;
import com.example.searchapp.shared.taxonomy.Taxonomy;
import com.example.searchapp.shared.taxonomy.TaxonomyLoader;
import com.example.searchapp.shared.web.RequestValidationException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * No Spring context, no Docker (§11.1): {@link DocumentClassifier} depends only on the loaded
 * {@link Taxonomy}, exercised here against the real bundled {@code taxonomy.yaml}.
 */
class DocumentClassifierTest {
  private final Taxonomy taxonomy = TaxonomyLoader.load(TaxonomyLoader.TAXONOMY_RESOURCE);
  private final DocumentClassifier classifier = new DocumentClassifier(taxonomy);

  @Test
  void classifiesByTitlePattern() {
    Classification classification =
        classifier.classify("2024 Utility Bill", "Nothing relevant here.", null, List.of());

    assertThat(classification.documentType()).isEqualTo("utility_bill");
    assertThat(classification.purposes()).containsExactly("proof_of_address");
    assertThat(classification.source()).isEqualTo(Classification.SOURCE_RULE);
    assertThat(classification.taxonomyVersion()).isEqualTo(taxonomy.version());
    assertThat(classification.labelText()).isEqualTo("utility bill proof of address");
  }

  @Test
  void classifiesByContentPatternAlone() {
    Classification classification =
        classifier.classify(
            "Coastline Power Statement",
            "Electricity 1225 kWh, standing charge 59.38p per day. Registered supply address as"
                + " shown. Smart meter readings taken automatically.",
            null,
            List.of());

    assertThat(classification.documentType()).isEqualTo("utility_bill");
    assertThat(classification.source()).isEqualTo(Classification.SOURCE_RULE);
  }

  @Test
  void isDeterministic() {
    Classification first =
        classifier.classify("2024 Utility Bill", "Account 123, due 15 June.", null, List.of());
    Classification second =
        classifier.classify("2024 Utility Bill", "Account 123, due 15 June.", null, List.of());

    assertThat(first).isEqualTo(second);
  }

  @Test
  void anUnrecognisedDocumentIsUnknownWithNoPurposes() {
    Classification classification =
        classifier.classify(
            "Holiday Itinerary", "Nothing here matches a pattern.", null, List.of());

    assertThat(classification.documentType()).isEqualTo(Taxonomy.UNKNOWN_TYPE);
    assertThat(classification.purposes()).isEmpty();
    assertThat(classification.source()).isEqualTo(Classification.SOURCE_UNKNOWN);
    assertThat(classification.labelText()).isEmpty();
  }

  @Test
  void aSingleContentPatternIsTooWeakToClassify() {
    // "beneficiaries" is one trust_deed content pattern (1 point): a letter of authority that
    // merely mentions beneficiaries is not a trust deed.
    Classification classification =
        classifier.classify(
            "Letter of Authority",
            "This authority does not permit the firm to change beneficiaries.",
            null,
            List.of());

    assertThat(classification.documentType()).isEqualTo(Taxonomy.UNKNOWN_TYPE);
    assertThat(classification.source()).isEqualTo(Classification.SOURCE_UNKNOWN);
  }

  @Test
  void aBroadbandBillIsAUtilityBillByItsTitle() {
    Classification classification =
        classifier.classify(
            "Broadband and Landline Bill July 2024", "Nothing else matches.", null, List.of());

    assertThat(classification.documentType()).isEqualTo("utility_bill");
    assertThat(classification.purposes()).containsExactly("proof_of_address");
  }

  @Test
  void aTieBetweenTwoTypesYieldsUnknownRatherThanAnArbitraryWinner() {
    // Title contains one title pattern from each of two types (2 points apiece) and nothing else.
    Classification classification =
        classifier.classify(
            "Council Tax Utility Bill", "No content patterns here.", null, List.of());

    assertThat(classification.documentType()).isEqualTo(Taxonomy.UNKNOWN_TYPE);
    assertThat(classification.source()).isEqualTo(Classification.SOURCE_UNKNOWN);
  }

  @Test
  void aRequestedTypeIsUsedWithDefaultPurposesAndSourceRequest() {
    Classification classification =
        classifier.classify("Some Title", "Some content.", "utility_bill", List.of());

    assertThat(classification.documentType()).isEqualTo("utility_bill");
    assertThat(classification.purposes()).containsExactly("proof_of_address");
    assertThat(classification.source()).isEqualTo(Classification.SOURCE_REQUEST);
    assertThat(classification.labelText()).isEqualTo("utility bill proof of address");
  }

  @Test
  void aRequestedTypeCanOverrideItsDefaultPurposes() {
    Classification classification =
        classifier.classify(
            "Some Title", "Some content.", "bank_statement", List.of("source_of_funds"));

    assertThat(classification.documentType()).isEqualTo("bank_statement");
    assertThat(classification.purposes()).containsExactly("source_of_funds");
    assertThat(classification.source()).isEqualTo(Classification.SOURCE_REQUEST);
  }

  @Test
  void rejectsAnUnrecognisedRequestedType() {
    assertThatThrownBy(() -> classifier.classify("Title", "Content", "not_a_real_type", List.of()))
        .isInstanceOf(RequestValidationException.class)
        .satisfies(
            exception ->
                assertThat(((RequestValidationException) exception).errors())
                    .containsKey("document_type"));
  }

  @Test
  void rejectsAnUnrecognisedRequestedPurpose() {
    assertThatThrownBy(
            () ->
                classifier.classify(
                    "Title", "Content", "utility_bill", List.of("not_a_real_purpose")))
        .isInstanceOf(RequestValidationException.class)
        .satisfies(
            exception ->
                assertThat(((RequestValidationException) exception).errors())
                    .containsKey("purposes[0]"));
  }

  @Test
  void rejectsPurposesGivenWithoutADocumentType() {
    assertThatThrownBy(
            () -> classifier.classify("Title", "Content", null, List.of("proof_of_address")))
        .isInstanceOf(RequestValidationException.class)
        .satisfies(
            exception ->
                assertThat(((RequestValidationException) exception).errors())
                    .containsKey("purposes"));
  }

  @Test
  void relabelKeepsTheGivenTypeAndPurposesButRefreshesTheLabel() {
    Classification classification =
        classifier.relabel(
            "bank_statement", List.of("source_of_funds"), Classification.SOURCE_REQUEST);

    assertThat(classification.documentType()).isEqualTo("bank_statement");
    assertThat(classification.purposes()).containsExactly("source_of_funds");
    assertThat(classification.source()).isEqualTo(Classification.SOURCE_REQUEST);
    assertThat(classification.labelText()).isEqualTo("bank statement source of funds");
    assertThat(classification.taxonomyVersion()).isEqualTo(taxonomy.version());
  }

  @Test
  void everySeedDocumentClassifiesToItsExpectedTypeWithNoTies() {
    DemoCorpus corpus = EvalCorpusLoader.corpus();
    Map<String, String> expected = EvalCorpusLoader.classification();

    List<String> failures =
        corpus.clients().stream()
            .flatMap(client -> client.documents().stream())
            .map(
                document -> {
                  String expectedType = expected.get(document.title());
                  if (expectedType == null) {
                    return "no expectation for title '" + document.title() + "'";
                  }
                  String actualType =
                      classifier
                          .classify(document.title(), document.content(), null, List.of())
                          .documentType();
                  return actualType.equals(expectedType)
                      ? null
                      : "'%s': expected '%s' but got '%s'"
                          .formatted(document.title(), expectedType, actualType);
                })
            .filter(Objects::nonNull)
            .toList();

    assertThat(failures).isEmpty();
  }
}
