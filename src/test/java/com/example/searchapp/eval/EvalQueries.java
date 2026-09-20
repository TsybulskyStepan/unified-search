package com.example.searchapp.eval;

import com.example.searchapp.onboarding.seed.DemoCorpus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The query set loaded from {@code src/test/resources/eval/queries.json} (§11.3). Every query
 * declares one expectation {@link Shape}; the shape decides its assertion. Expected items are
 * hand-labelled by what the document is, never read back from a search result.
 *
 * <p>{@code sets} names lists of expected documents shared by several queries ({@code address
 * proof} and {@code proof of address} answer the same question).
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
    NONE
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

  /**
   * Expands an entry to the concrete corpus items it names, in corpus order: the query's named set
   * first, then its own entries.
   */
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

  /**
   * The (query, document) pairs the semantic signal is expected to recall: every expected document
   * of a {@code first} or {@code all_within} query. A compound query's document is retrieved on its
   * residual text, not on the whole query, so it does not measure the floor.
   */
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
