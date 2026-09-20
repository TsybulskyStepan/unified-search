package com.example.searchapp.shared.taxonomy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.jupiter.api.Test;

/**
 * No Spring context, no Docker (§11.1): {@link TaxonomyLoader#parse} is a pure function from YAML
 * text to a validated {@link Taxonomy}, exercised directly against inline fixtures the way {@code
 * ApiKeyPropertiesTest} exercises {@code ApiKeyProperties}.
 */
class TaxonomyLoaderTest {

  private static final Map<String, List<String>> EXPECTED_TYPE_PURPOSES =
      Map.ofEntries(
          Map.entry("utility_bill", List.of("proof_of_address")),
          Map.entry("council_tax_bill", List.of("proof_of_address")),
          Map.entry("bank_statement", List.of("proof_of_address")),
          Map.entry("tenancy_agreement", List.of("proof_of_address")),
          Map.entry("passport", List.of("proof_of_identity")),
          Map.entry("driving_licence", List.of("proof_of_identity")),
          Map.entry("w9", List.of("tax_status")),
          Map.entry("tax_return", List.of("tax_status")),
          Map.entry("completion_statement", List.of("source_of_funds")),
          Map.entry("engagement_letter", List.of("fees_and_terms")),
          Map.entry("investment_policy_statement", List.of("investment_mandate")),
          Map.entry("trust_deed", List.of("trust_structure")));

  private static final List<String> EXPECTED_PURPOSE_IDS =
      List.of(
          "proof_of_address",
          "proof_of_identity",
          "source_of_funds",
          "tax_status",
          "fees_and_terms",
          "investment_mandate",
          "trust_structure");

  @Test
  void loadsTheBundledTaxonomy() {
    Taxonomy taxonomy = TaxonomyLoader.load(TaxonomyLoader.TAXONOMY_RESOURCE);

    assertThat(taxonomy.version()).isEqualTo(1);
    assertThat(taxonomy.types()).hasSize(EXPECTED_TYPE_PURPOSES.size() + 1); // + unknown
    assertThat(taxonomy.purposes()).hasSize(EXPECTED_PURPOSE_IDS.size());
  }

  @Test
  void everyDocumentedTypeIsPresentWithItsFieldsAndDefaultPurposes() {
    Taxonomy taxonomy = TaxonomyLoader.load(TaxonomyLoader.TAXONOMY_RESOURCE);

    EXPECTED_TYPE_PURPOSES.forEach(
        (id, expectedPurposes) -> {
          Taxonomy.DocumentType type = taxonomy.types().get(id);
          assertThat(type).as("type '%s'", id).isNotNull();
          assertThat(type.id()).isEqualTo(id);
          assertThat(type.label()).as("label of '%s'", id).isNotBlank();
          assertThat(type.defaultPurposes()).as("purposes of '%s'", id).isEqualTo(expectedPurposes);
          assertThat(type.titlePatterns()).as("title patterns of '%s'", id).isNotEmpty();
          assertThat(type.contentPatterns()).as("content patterns of '%s'", id).isNotEmpty();
        });
  }

  @Test
  void everyDocumentedPurposeIsPresentWithItsFields() {
    Taxonomy taxonomy = TaxonomyLoader.load(TaxonomyLoader.TAXONOMY_RESOURCE);

    for (String id : EXPECTED_PURPOSE_IDS) {
      Taxonomy.Purpose purpose = taxonomy.purposes().get(id);
      assertThat(purpose).as("purpose '%s'", id).isNotNull();
      assertThat(purpose.id()).isEqualTo(id);
      assertThat(purpose.label()).as("label of '%s'", id).isNotBlank();
      assertThat(purpose.synonyms()).as("synonyms of '%s'", id).isNotEmpty();
    }
  }

  @Test
  void unknownIsALegalTypeCarryingNoPurposes() {
    Taxonomy taxonomy = TaxonomyLoader.load(TaxonomyLoader.TAXONOMY_RESOURCE);

    Taxonomy.DocumentType unknown = taxonomy.types().get(Taxonomy.UNKNOWN_TYPE);
    assertThat(unknown).isNotNull();
    assertThat(unknown.defaultPurposes()).isEmpty();
  }

  @Test
  void failsLoudlyWhenTheResourceIsMissing() {
    assertThatThrownBy(() -> TaxonomyLoader.load("/taxonomy/does-not-exist.yaml"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Missing taxonomy file");
  }

  @Test
  void rejectsAMissingOrNonIntegerVersion() {
    String yaml =
        """
        types:
          utility_bill:
            label: utility bill
            purposes: [proof_of_address]
            title_patterns: [utility bill]
            content_patterns: [kwh]
            synonyms: [bill]
        purposes:
          proof_of_address:
            label: proof of address
            synonyms: [proof of address]
        """;

    assertThatFailsToParse(yaml).hasMessageContaining("version").hasMessageContaining("integer");
  }

  @Test
  void rejectsADuplicateTypeId() {
    String yaml =
        """
        version: 1
        types:
          utility_bill:
            label: utility bill
            purposes: [proof_of_address]
            title_patterns: [utility bill]
            content_patterns: [kwh]
            synonyms: [bill]
          utility_bill:
            label: duplicate
            purposes: [proof_of_address]
            title_patterns: []
            content_patterns: []
            synonyms: []
        purposes:
          proof_of_address:
            label: proof of address
            synonyms: [proof of address]
        """;

    assertThatFailsToParse(yaml).hasMessageContaining("duplicate key");
  }

  @Test
  void rejectsADuplicatePurposeId() {
    String yaml =
        """
        version: 1
        types:
          utility_bill:
            label: utility bill
            purposes: [proof_of_address]
            title_patterns: [utility bill]
            content_patterns: [kwh]
            synonyms: [bill]
        purposes:
          proof_of_address:
            label: proof of address
            synonyms: [proof of address]
          proof_of_address:
            label: duplicate
            synonyms: []
        """;

    assertThatFailsToParse(yaml).hasMessageContaining("duplicate key");
  }

  @Test
  void rejectsATypeNamingAPurposeThatDoesNotExist() {
    String yaml =
        """
        version: 1
        types:
          utility_bill:
            label: utility bill
            purposes: [does_not_exist]
            title_patterns: [utility bill]
            content_patterns: [kwh]
            synonyms: [bill]
        purposes:
          proof_of_address:
            label: proof of address
            synonyms: [proof of address]
        """;

    assertThatFailsToParse(yaml)
        .hasMessageContaining("utility_bill")
        .hasMessageContaining("does_not_exist");
  }

  @Test
  void rejectsASynonymThatCollidesWithATypeId() {
    String yaml =
        """
        version: 1
        types:
          utility_bill:
            label: utility bill
            purposes: [proof_of_address]
            title_patterns: [utility bill]
            content_patterns: [kwh]
            synonyms: [utility_bill]
        purposes:
          proof_of_address:
            label: proof of address
            synonyms: [proof of address]
        """;

    assertThatFailsToParse(yaml).hasMessageContaining("collides with a type or purpose id");
  }

  @Test
  void rejectsASynonymThatCollidesWithAPurposeId() {
    String yaml =
        """
        version: 1
        types:
          utility_bill:
            label: utility bill
            purposes: [proof_of_address]
            title_patterns: [utility bill]
            content_patterns: [kwh]
            synonyms: [proof_of_address]
        purposes:
          proof_of_address:
            label: proof of address
            synonyms: [proof of address]
        """;

    assertThatFailsToParse(yaml).hasMessageContaining("collides with a type or purpose id");
  }

  @Test
  void rejectsAnIdThatNamesBothATypeAndAPurpose() {
    String yaml =
        """
        version: 1
        types:
          proof_of_address:
            label: proof of address form
            purposes: [proof_of_address]
            title_patterns: [proof of address]
            content_patterns: [address]
            synonyms: [address proof]
        purposes:
          proof_of_address:
            label: proof of address
            synonyms: [residence proof]
        """;

    assertThatFailsToParse(yaml)
        .hasMessageContaining("proof_of_address")
        .hasMessageContaining("both a type and a purpose id");
  }

  @Test
  void rejectsAFileThatDefinesTheReservedUnknownType() {
    String yaml =
        """
        version: 1
        types:
          unknown:
            label: unknown
            purposes: []
            title_patterns: []
            content_patterns: []
            synonyms: []
        purposes:
          proof_of_address:
            label: proof of address
            synonyms: [proof of address]
        """;

    assertThatFailsToParse(yaml).hasMessageContaining("reserved");
  }

  @Test
  void rejectsAFileThatDefinesTheReservedUnknownPurpose() {
    String yaml =
        """
        version: 1
        types:
          utility_bill:
            label: utility bill
            purposes: []
            title_patterns: [utility bill]
            content_patterns: [kwh]
            synonyms: [bill]
        purposes:
          unknown:
            label: unknown
            synonyms: []
        """;

    assertThatFailsToParse(yaml).hasMessageContaining("reserved");
  }

  private static AbstractThrowableAssert<?, ? extends Throwable> assertThatFailsToParse(
      String yaml) {
    return assertThatThrownBy(
            () ->
                TaxonomyLoader.parse(
                    new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "<test>"))
        .isInstanceOf(IllegalStateException.class);
  }
}
