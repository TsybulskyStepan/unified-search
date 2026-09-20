package com.example.searchapp.eval;

import com.example.searchapp.onboarding.seed.DemoCorpus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The query set in {@code eval/queries.json} (§11.3); {@code sets} are answer lists queries share.
 */
public record EvalQueries(Map<String, List<Expected>> sets, List<EvalQuery> queries) {

  public enum Shape {
    @JsonProperty("first")
    FIRST,
    @JsonProperty("all_within")
    ALL_WITHIN,
    @JsonProperty("compound")
    COMPOUND,
    @JsonProperty("none")
    NONE,
    /**
     * Text that is not language (§6.3): no results, but unlike {@link #NONE} it is not a negative
     * for the semantic floor, because a readability gate rejects it rather than the floor.
     */
    @JsonProperty("gibberish")
    GIBBERISH
  }

  /**
   * A client ({@code client} only), or documents ({@code title}, optionally narrowed to one
   * client). A title alone means every document in the corpus with that title.
   */
  public record Expected(String client, String title) {}

  public record EvalQuery(String query, Shape shape, String expectedSet, List<Expected> expected) {}

  /** One resolved expected result: a client, or one document of a client. */
  public record Item(String clientEmail, String title) {
    public boolean isDocument() {
      return title != null;
    }
  }

  /** The corpus items a query expects: its named set first, then its own entries. */
  public List<Item> resolve(EvalQuery query, DemoCorpus corpus) {
    List<Expected> entries = new ArrayList<>();
    if (query.expectedSet() != null) {
      entries.addAll(sets.get(query.expectedSet()));
    }
    if (query.expected() != null) {
      entries.addAll(query.expected());
    }
    List<Item> items = new ArrayList<>();
    for (Expected entry : entries) {
      if (entry.title() == null) {
        items.add(new Item(entry.client(), null));
        continue;
      }
      List<Item> matches =
          corpus.clients().stream()
              .filter(client -> entry.client() == null || client.email().equals(entry.client()))
              .flatMap(
                  client ->
                      client.documents().stream()
                          .filter(document -> document.title().equals(entry.title()))
                          .map(document -> new Item(client.email(), document.title())))
              .toList();
      if (matches.isEmpty()) {
        throw new IllegalStateException("expected entry matches no corpus document: " + entry);
      }
      items.addAll(matches);
    }
    return items;
  }

  /** Documents the semantic signal must recall; compound queries search on a residual, so skip. */
  public List<Item> semanticPositives(EvalQuery query, DemoCorpus corpus) {
    if (query.shape() != Shape.FIRST && query.shape() != Shape.ALL_WITHIN) {
      return List.of();
    }
    return resolve(query, corpus).stream().filter(Item::isDocument).toList();
  }

  public List<String> negatives() {
    return queries.stream()
        .filter(query -> query.shape() == Shape.NONE)
        .map(EvalQuery::query)
        .toList();
  }
}
