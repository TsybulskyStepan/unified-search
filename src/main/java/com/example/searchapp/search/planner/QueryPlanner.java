package com.example.searchapp.search.planner;

import com.example.searchapp.shared.taxonomy.Taxonomy;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Converts normalized query text and leading-run identity candidates into one search plan. */
@Component
public class QueryPlanner {
  private static final Pattern POSSESSIVE = Pattern.compile("(.+?)(?:'s|’s)$");

  private final List<IntentPhrase> intentPhrases;
  private final Set<String> singleTokenSynonyms;

  public QueryPlanner(Taxonomy taxonomy) {
    Map<List<String>, Set<String>> idsByPhrase = new LinkedHashMap<>();
    taxonomy.types().values().forEach(type -> addPhrases(idsByPhrase, type.id(), type.synonyms()));
    taxonomy
        .purposes()
        .values()
        .forEach(purpose -> addPhrases(idsByPhrase, purpose.id(), purpose.synonyms()));
    intentPhrases =
        idsByPhrase.entrySet().stream()
            .map(entry -> new IntentPhrase(entry.getKey(), entry.getValue()))
            .sorted(
                Comparator.comparingInt((IntentPhrase phrase) -> phrase.tokens().size()).reversed())
            .toList();
    singleTokenSynonyms =
        intentPhrases.stream()
            .filter(phrase -> phrase.tokens().size() == 1)
            .map(phrase -> phrase.tokens().getFirst())
            .collect(Collectors.toUnmodifiableSet());
  }

  public NormalizedQuery normalize(String query) {
    String normalized = Normalizer.normalize(query, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    String[] rawTokens = normalized.trim().split("\\s+");
    List<NormalizedToken> tokens = new ArrayList<>(rawTokens.length);
    for (String rawToken : rawTokens) {
      Matcher matcher = POSSESSIVE.matcher(rawToken);
      boolean possessive = matcher.matches();
      tokens.add(new NormalizedToken(possessive ? matcher.group(1) : rawToken, possessive));
    }
    return new NormalizedQuery(
        tokens.stream().map(NormalizedToken::text).collect(Collectors.joining(" ")), tokens);
  }

  public QueryPlan plan(String query, List<MentionCandidate> candidates) {
    return plan(normalize(query), candidates);
  }

  public QueryPlan plan(NormalizedQuery query, List<MentionCandidate> candidates) {
    int longestMatch =
        candidates.stream().mapToInt(MentionCandidate::matchedThroughPosition).max().orElse(0);
    List<ClientMention> mentions = mentions(query, candidates, longestMatch);
    int consumedTokens = mentions.isEmpty() ? 0 : longestMatch;
    List<String> residualTokens =
        query.tokens().subList(consumedTokens, query.tokens().size()).stream()
            .map(NormalizedToken::text)
            .toList();
    String residual = String.join(" ", residualTokens);
    return new QueryPlan(query.text(), mentions, residual, intents(residualTokens));
  }

  /** Every client tied on the longest leading run: one when the name is unique, more when not. */
  private List<ClientMention> mentions(
      NormalizedQuery query, List<MentionCandidate> candidates, int longestMatch) {
    if (longestMatch == 0
        || (longestMatch == 1
            && singleTokenSynonyms.contains(canonical(query.tokens().getFirst().text()))
            && !query.tokens().getFirst().possessive())) {
      return List.of();
    }
    return candidates.stream()
        .filter(candidate -> candidate.matchedThroughPosition() == longestMatch)
        .map(
            candidate ->
                new ClientMention(candidate.clientId(), candidate.field(), candidate.score()))
        .toList();
  }

  private Set<String> intents(List<String> tokens) {
    boolean[] consumed = new boolean[tokens.size()];
    Set<String> intents = new LinkedHashSet<>();
    for (IntentPhrase phrase : intentPhrases) {
      for (int start = 0; start <= tokens.size() - phrase.tokens().size(); start++) {
        if (matches(tokens, consumed, start, phrase.tokens())) {
          intents.addAll(phrase.ids());
          for (int index = start; index < start + phrase.tokens().size(); index++) {
            consumed[index] = true;
          }
        }
      }
    }
    return intents;
  }

  private static boolean matches(
      List<String> tokens, boolean[] consumed, int start, List<String> phraseTokens) {
    for (int index = 0; index < phraseTokens.size(); index++) {
      if (consumed[start + index]
          || !canonical(tokens.get(start + index)).equals(phraseTokens.get(index))) {
        return false;
      }
    }
    return true;
  }

  private static void addPhrases(
      Map<List<String>, Set<String>> idsByPhrase, String id, List<String> synonyms) {
    for (String synonym : synonyms) {
      List<String> tokens =
          Arrays.stream(
                  Normalizer.normalize(synonym, Normalizer.Form.NFKC)
                      .toLowerCase(Locale.ROOT)
                      .split("\\s+"))
              .map(QueryPlanner::canonical)
              .toList();
      idsByPhrase.computeIfAbsent(tokens, ignored -> new LinkedHashSet<>()).add(id);
    }
  }

  private static String canonical(String token) {
    return token.replace("-", "");
  }

  private record IntentPhrase(List<String> tokens, Set<String> ids) {
    private IntentPhrase {
      tokens = List.copyOf(tokens);
      ids = Set.copyOf(ids);
    }
  }
}
