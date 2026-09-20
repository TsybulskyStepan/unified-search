package com.example.searchapp.search.planner;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.searchapp.shared.taxonomy.Taxonomy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class QueryPlannerTest {
  private static final UUID JOHN = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID MARY = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID BILL = UUID.fromString("00000000-0000-0000-0000-000000000003");
  private static final QueryPlanner PLANNER = new QueryPlanner(taxonomy());

  @ParameterizedTest(name = "{0}")
  @MethodSource("queries")
  void plansWorkedExamplesAndMentionEdges(
      String name,
      String query,
      List<MentionCandidate> candidates,
      List<UUID> mentionedClients,
      String residual,
      Set<String> intents) {
    QueryPlan plan = PLANNER.plan(query, candidates);

    assertThat(plan.mentions())
        .extracting(ClientMention::clientId)
        .containsExactlyElementsOf(mentionedClients);
    assertThat(plan.residual()).isEqualTo(residual);
    assertThat(plan.intents()).containsExactlyInAnyOrderElementsOf(intents);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("normalization")
  void normalizesBeforePlanning(String name, String query, String normalized) {
    assertThat(PLANNER.plan(query, List.of()).query()).isEqualTo(normalized);
  }

  private static Stream<Arguments> normalization() {
    return Stream.of(
        Arguments.of("case and whitespace", "  JOHN   utility\t bill ", "john utility bill"),
        Arguments.of("ASCII possessive", "John's bill", "john bill"),
        Arguments.of("curly possessive", "Bill’s statement", "bill statement"),
        Arguments.of("NFKC hyphen", "Ｗ－９", "w-9"));
  }

  private static Stream<Arguments> queries() {
    return Stream.of(
        row("identity name", "John", john(1), JOHN, "", Set.of()),
        row("identity email", "NevisWealth", emailJohn(1), JOHN, "", Set.of()),
        row("misspelled identity", "Hendersen", mary(1), MARY, "", Set.of()),
        row(
            "category does not scope Bill",
            "utility bill",
            List.of(),
            null,
            "utility bill",
            Set.of("utility_bill")),
        row(
            "possessive scopes John",
            "John's bill",
            john(1),
            JOHN,
            "bill",
            Set.of("utility_bill", "council_tax_bill")),
        row(
            "possessive lifts category-name ambiguity",
            "Bill's statement",
            bill(1),
            BILL,
            "statement",
            Set.of("bank_statement")),
        row(
            "bare category-name ambiguity stays a category",
            "bill",
            bill(1),
            null,
            "bill",
            Set.of("utility_bill", "council_tax_bill")),
        row("surname resolves ambiguity", "Bill Carter", bill(2), BILL, "", Set.of()),
        row(
            "tax residency",
            "tax residency",
            List.of(),
            null,
            "tax residency",
            Set.of("tax_status")),
        row(
            "source of funds",
            "source of funds",
            List.of(),
            null,
            "source of funds",
            Set.of("source_of_funds")),
        row(
            "proof of address",
            "proof of address",
            List.of(),
            null,
            "proof of address",
            Set.of("proof_of_address")),
        row(
            "advisory fees",
            "advisory fees",
            List.of(),
            null,
            "advisory fees",
            Set.of("fees_and_terms")),
        row(
            "longest phrase wins",
            "completion statement",
            List.of(),
            null,
            "completion statement",
            Set.of("completion_statement")),
        tied(
            "two clients tied on the longest run are both mentioned",
            "John Mary",
            List.of(
                new MentionCandidate(JOHN, "name", 1.0, 1),
                new MentionCandidate(MARY, "name", 1.0, 1)),
            List.of(JOHN, MARY),
            "mary",
            Set.of()),
        tied(
            "an ambiguous first name still leaves the category text as the residual",
            "John utility bill",
            List.of(
                new MentionCandidate(JOHN, "name", 1.0, 1),
                new MentionCandidate(MARY, "name", 1.0, 1)),
            List.of(JOHN, MARY),
            "utility bill",
            Set.of("utility_bill")),
        tied(
            "the category-name rule also blocks a tie",
            "bill",
            List.of(
                new MentionCandidate(BILL, "name", 1.0, 1),
                new MentionCandidate(MARY, "name", 1.0, 1)),
            List.of(),
            "bill",
            Set.of("utility_bill", "council_tax_bill")),
        tied(
            "a possessive lifts the category-name rule for a tie",
            "Bill's statement",
            List.of(
                new MentionCandidate(BILL, "name", 1.0, 1),
                new MentionCandidate(MARY, "name", 1.0, 1)),
            List.of(BILL, MARY),
            "statement",
            Set.of("bank_statement")),
        row(
            "longer leading identity beats a partial match",
            "John Doe utility bill",
            List.of(
                new MentionCandidate(JOHN, "name", 1.0, 2),
                new MentionCandidate(MARY, "name", 1.0, 1)),
            JOHN,
            "utility bill",
            Set.of("utility_bill")),
        row(
            "unmatched text still searches",
            "meeting notes",
            List.of(),
            null,
            "meeting notes",
            Set.of()),
        row("NFKC and hyphen form", "  Ｗ－９  ", List.of(), null, "w-9", Set.of("w9")),
        row("dehyphenated form", "w9", List.of(), null, "w9", Set.of("w9")));
  }

  private static Arguments row(
      String name,
      String query,
      List<MentionCandidate> candidates,
      UUID mentionedClient,
      String residual,
      Set<String> intents) {
    return tied(
        name,
        query,
        candidates,
        mentionedClient == null ? List.of() : List.of(mentionedClient),
        residual,
        intents);
  }

  private static Arguments tied(
      String name,
      String query,
      List<MentionCandidate> candidates,
      List<UUID> mentionedClients,
      String residual,
      Set<String> intents) {
    return Arguments.of(name, query, candidates, mentionedClients, residual, intents);
  }

  private static List<MentionCandidate> john(int tokenCount) {
    return List.of(new MentionCandidate(JOHN, "name", 1.0, tokenCount));
  }

  private static List<MentionCandidate> emailJohn(int tokenCount) {
    return List.of(new MentionCandidate(JOHN, "email", 1.0, tokenCount));
  }

  private static List<MentionCandidate> mary(int tokenCount) {
    return List.of(new MentionCandidate(MARY, "name", 0.7, tokenCount));
  }

  private static List<MentionCandidate> bill(int tokenCount) {
    return List.of(new MentionCandidate(BILL, "name", 1.0, tokenCount));
  }

  private static Taxonomy taxonomy() {
    return new Taxonomy(
        1,
        Map.of(
            "utility_bill", type("utility_bill", "utility bill", "utility bill", "bill"),
            "council_tax_bill", type("council_tax_bill", "council tax bill", "bill"),
            "bank_statement", type("bank_statement", "bank statement", "statement"),
            "completion_statement",
                type("completion_statement", "completion statement", "completion statement"),
            "w9", type("w9", "w-9 form", "w-9")),
        Map.of(
            "tax_status", purpose("tax_status", "tax residency"),
            "source_of_funds", purpose("source_of_funds", "source of funds"),
            "proof_of_address", purpose("proof_of_address", "proof of address"),
            "fees_and_terms", purpose("fees_and_terms", "advisory fees")));
  }

  private static Taxonomy.DocumentType type(String id, String label, String... synonyms) {
    return new Taxonomy.DocumentType(id, label, List.of(), List.of(), List.of(), List.of(synonyms));
  }

  private static Taxonomy.Purpose purpose(String id, String... synonyms) {
    return new Taxonomy.Purpose(id, id, List.of(synonyms));
  }
}
