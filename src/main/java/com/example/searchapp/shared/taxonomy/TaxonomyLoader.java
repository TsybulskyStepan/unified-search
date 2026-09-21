package com.example.searchapp.shared.taxonomy;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Loads and validates {@code taxonomy.yaml} (§3.2) into the {@link Taxonomy} bean every other
 * module reads through {@code shared}. Validation is the point of this class, not an afterthought:
 * the classifier and the planner (future tickets) never see the file, only this bean, so a
 * structural mistake here — a duplicate id, a type naming a purpose that doesn't exist, a synonym
 * that collides with an id — has to fail startup, or it fails silently in two different modules
 * months apart instead.
 */
@Configuration
public class TaxonomyLoader {
  public static final String TAXONOMY_RESOURCE = "/taxonomy/taxonomy.yaml";

  @Bean
  Taxonomy taxonomy() {
    return load(TAXONOMY_RESOURCE);
  }

  /**
   * Public so a unit test outside this package (e.g. {@code DocumentClassifierTest}, §11.1) can
   * load the real bundled taxonomy without a Spring context.
   */
  public static Taxonomy load(String resource) {
    try (InputStream in = TaxonomyLoader.class.getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("Missing taxonomy file on classpath: " + resource);
      }
      return parse(in, resource);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read taxonomy file: " + resource, e);
    }
  }

  /** Package-visible so tests can exercise validation against inline YAML fixtures. */
  static Taxonomy parse(InputStream in, String source) {
    return new Parser(source).parse(in);
  }

  /** Parses one taxonomy file; {@code source} prefixes every error so it names the file. */
  private record Parser(String source) {
    Taxonomy parse(InputStream in) {
      Map<String, Object> root = readYaml(in);

      if (!(root.get("version") instanceof Integer version)) {
        throw invalid("'version' must be an integer");
      }

      Map<String, Purpose> purposes = parsePurposes(root);
      Map<String, DocumentType> types = parseTypes(root, purposes);
      checkIdsDisjoint(types, purposes);
      types.put(Taxonomy.UNKNOWN_TYPE, unknownType());

      checkSynonymCollisions(types, purposes);

      return new Taxonomy(version, types, purposes);
    }

    private Map<String, Purpose> parsePurposes(Map<String, Object> root) {
      Map<String, Purpose> purposes = new LinkedHashMap<>();
      for (Map.Entry<String, Object> entry : mapOf(root.get("purposes"), "purposes").entrySet()) {
        String id = entry.getKey();
        String path = "purposes." + id;
        requireNotReserved(id, "purpose");
        Map<String, Object> fields = mapOf(entry.getValue(), path);
        purposes.put(
            id,
            new Purpose(
                id, stringOf(fields, path, "label"), stringListOf(fields, path, "synonyms")));
      }
      return purposes;
    }

    private Map<String, DocumentType> parseTypes(
        Map<String, Object> root, Map<String, Purpose> purposes) {
      Map<String, DocumentType> types = new LinkedHashMap<>();
      for (Map.Entry<String, Object> entry : mapOf(root.get("types"), "types").entrySet()) {
        String id = entry.getKey();
        String path = "types." + id;
        requireNotReserved(id, "type");
        Map<String, Object> fields = mapOf(entry.getValue(), path);
        List<String> defaultPurposes = stringListOf(fields, path, "purposes");
        for (String purposeId : defaultPurposes) {
          if (!purposes.containsKey(purposeId)) {
            throw invalid("type '" + id + "' names unknown purpose '" + purposeId + "'");
          }
        }
        types.put(
            id,
            new DocumentType(
                id,
                stringOf(fields, path, "label"),
                defaultPurposes,
                stringListOf(fields, path, "title_patterns"),
                stringListOf(fields, path, "content_patterns"),
                stringListOf(fields, path, "synonyms")));
      }
      return types;
    }

    private static DocumentType unknownType() {
      return new DocumentType(
          Taxonomy.UNKNOWN_TYPE, Taxonomy.UNKNOWN_TYPE, List.of(), List.of(), List.of(), List.of());
    }

    private void requireNotReserved(String id, String kind) {
      if (Taxonomy.UNKNOWN_TYPE.equals(id)) {
        throw invalid("'" + id + "' is a reserved " + kind + " id");
      }
    }

    /**
     * The planner splits a query's intent ids into types and purposes by looking each up in the
     * matching map, so an id in both would be counted and matched as each.
     */
    private void checkIdsDisjoint(Map<String, DocumentType> types, Map<String, Purpose> purposes) {
      for (String id : types.keySet()) {
        if (purposes.containsKey(id)) {
          throw invalid("'" + id + "' is both a type and a purpose id");
        }
      }
    }

    /**
     * A synonym equal to a type or purpose id is ambiguous to anything resolving free text against
     * this taxonomy (§3.1): it would be unclear whether the token names that entity directly or was
     * meant to route to whatever the synonym maps to.
     */
    private void checkSynonymCollisions(
        Map<String, DocumentType> types, Map<String, Purpose> purposes) {
      Set<String> ids = new HashSet<>(types.keySet());
      ids.addAll(purposes.keySet());
      types.values().forEach(type -> checkSynonyms(ids, "type", type.id(), type.synonyms()));
      purposes
          .values()
          .forEach(purpose -> checkSynonyms(ids, "purpose", purpose.id(), purpose.synonyms()));
    }

    private void checkSynonyms(Set<String> ids, String kind, String id, List<String> synonyms) {
      for (String synonym : synonyms) {
        if (ids.contains(synonym)) {
          throw invalid(
              "synonym '"
                  + synonym
                  + "' of "
                  + kind
                  + " '"
                  + id
                  + "' collides with a type or purpose id");
        }
      }
    }

    private Map<String, Object> readYaml(InputStream in) {
      LoaderOptions options = new LoaderOptions();
      options.setAllowDuplicateKeys(false);
      Yaml yaml = new Yaml(new SafeConstructor(options));
      Object loaded;
      try {
        loaded = yaml.load(in);
      } catch (YAMLException e) {
        throw new IllegalStateException(source + ": invalid YAML: " + e.getMessage(), e);
      }
      return mapOf(loaded, "<root>");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapOf(Object value, String path) {
      if (!(value instanceof Map<?, ?> map)) {
        throw invalid("'" + path + "' must be a mapping");
      }
      return (Map<String, Object>) map;
    }

    private String stringOf(Map<String, Object> fields, String path, String key) {
      if (!(fields.get(key) instanceof String string) || string.isBlank()) {
        throw invalid("'" + path + "." + key + "' must be a non-blank string");
      }
      return string;
    }

    private List<String> stringListOf(Map<String, Object> fields, String path, String key) {
      Object value = fields.get(key);
      if (value == null) {
        return List.of();
      }
      if (!(value instanceof List<?> list)) {
        throw invalid("'" + path + "." + key + "' must be a list");
      }
      List<String> result = new ArrayList<>();
      for (Object element : list) {
        if (!(element instanceof String string) || string.isBlank()) {
          throw invalid("'" + path + "." + key + "' must contain only non-blank strings");
        }
        result.add(string);
      }
      return result;
    }

    private IllegalStateException invalid(String message) {
      return new IllegalStateException(source + ": " + message);
    }
  }
}
