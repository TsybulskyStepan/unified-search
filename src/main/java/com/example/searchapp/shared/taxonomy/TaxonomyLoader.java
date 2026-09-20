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
class TaxonomyLoader {
  static final String TAXONOMY_RESOURCE = "/taxonomy/taxonomy.yaml";

  @Bean
  Taxonomy taxonomy() {
    return load(TAXONOMY_RESOURCE);
  }

  /** Package-visible for direct testing, without a Spring context. */
  static Taxonomy load(String resource) {
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
    Map<String, Object> root = readYaml(in, source);

    if (!(root.get("version") instanceof Integer version)) {
      throw new IllegalStateException(source + ": 'version' must be an integer");
    }

    Map<String, Taxonomy.Purpose> purposes = parsePurposes(root, source);
    Map<String, Taxonomy.DocumentType> types = parseTypes(root, source, purposes);
    types.put(Taxonomy.UNKNOWN_TYPE, unknownType());

    checkSynonymCollisions(types, purposes, source);

    return new Taxonomy(version, types, purposes);
  }

  private static Map<String, Taxonomy.Purpose> parsePurposes(
      Map<String, Object> root, String source) {
    Map<String, Taxonomy.Purpose> purposes = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry :
        mapOf(root.get("purposes"), source, "purposes").entrySet()) {
      String id = entry.getKey();
      requireNotReserved(id, source, "purpose");
      Map<String, Object> fields = mapOf(entry.getValue(), source, "purposes." + id);
      purposes.put(
          id,
          new Taxonomy.Purpose(
              id,
              stringOf(fields.get("label"), source, "purposes." + id + ".label"),
              stringListOf(fields.get("synonyms"), source, "purposes." + id + ".synonyms")));
    }
    return purposes;
  }

  private static Map<String, Taxonomy.DocumentType> parseTypes(
      Map<String, Object> root, String source, Map<String, Taxonomy.Purpose> purposes) {
    Map<String, Taxonomy.DocumentType> types = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : mapOf(root.get("types"), source, "types").entrySet()) {
      String id = entry.getKey();
      requireNotReserved(id, source, "type");
      Map<String, Object> fields = mapOf(entry.getValue(), source, "types." + id);
      List<String> defaultPurposes =
          stringListOf(fields.get("purposes"), source, "types." + id + ".purposes");
      for (String purposeId : defaultPurposes) {
        if (!purposes.containsKey(purposeId)) {
          throw new IllegalStateException(
              source + ": type '" + id + "' names unknown purpose '" + purposeId + "'");
        }
      }
      types.put(
          id,
          new Taxonomy.DocumentType(
              id,
              stringOf(fields.get("label"), source, "types." + id + ".label"),
              defaultPurposes,
              stringListOf(fields.get("title_patterns"), source, "types." + id + ".title_patterns"),
              stringListOf(
                  fields.get("content_patterns"), source, "types." + id + ".content_patterns"),
              stringListOf(fields.get("synonyms"), source, "types." + id + ".synonyms")));
    }
    return types;
  }

  private static Taxonomy.DocumentType unknownType() {
    return new Taxonomy.DocumentType(
        Taxonomy.UNKNOWN_TYPE, Taxonomy.UNKNOWN_TYPE, List.of(), List.of(), List.of(), List.of());
  }

  private static void requireNotReserved(String id, String source, String kind) {
    if (Taxonomy.UNKNOWN_TYPE.equals(id)) {
      throw new IllegalStateException(source + ": '" + id + "' is a reserved " + kind + " id");
    }
  }

  /**
   * A synonym equal to a type or purpose id is ambiguous to anything resolving free text against
   * this taxonomy (§3.1): it would be unclear whether the token names that entity directly or was
   * meant to route to whatever the synonym maps to.
   */
  private static void checkSynonymCollisions(
      Map<String, Taxonomy.DocumentType> types,
      Map<String, Taxonomy.Purpose> purposes,
      String source) {
    Set<String> ids = new HashSet<>();
    ids.addAll(types.keySet());
    ids.addAll(purposes.keySet());
    for (Taxonomy.DocumentType type : types.values()) {
      for (String synonym : type.synonyms()) {
        if (ids.contains(synonym)) {
          throw new IllegalStateException(
              source
                  + ": synonym '"
                  + synonym
                  + "' of type '"
                  + type.id()
                  + "' collides with a type or purpose id");
        }
      }
    }
    for (Taxonomy.Purpose purpose : purposes.values()) {
      for (String synonym : purpose.synonyms()) {
        if (ids.contains(synonym)) {
          throw new IllegalStateException(
              source
                  + ": synonym '"
                  + synonym
                  + "' of purpose '"
                  + purpose.id()
                  + "' collides with a type or purpose id");
        }
      }
    }
  }

  private static Map<String, Object> readYaml(InputStream in, String source) {
    LoaderOptions options = new LoaderOptions();
    options.setAllowDuplicateKeys(false);
    Yaml yaml = new Yaml(new SafeConstructor(options));
    Object loaded;
    try {
      loaded = yaml.load(in);
    } catch (YAMLException e) {
      throw new IllegalStateException(source + ": invalid YAML: " + e.getMessage(), e);
    }
    return mapOf(loaded, source, "<root>");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> mapOf(Object value, String source, String path) {
    if (!(value instanceof Map<?, ?> map)) {
      throw new IllegalStateException(source + ": '" + path + "' must be a mapping");
    }
    return (Map<String, Object>) map;
  }

  private static String stringOf(Object value, String source, String path) {
    if (!(value instanceof String string) || string.isBlank()) {
      throw new IllegalStateException(source + ": '" + path + "' must be a non-blank string");
    }
    return string;
  }

  private static List<String> stringListOf(Object value, String source, String path) {
    if (value == null) {
      return List.of();
    }
    if (!(value instanceof List<?> list)) {
      throw new IllegalStateException(source + ": '" + path + "' must be a list");
    }
    List<String> result = new ArrayList<>();
    for (Object element : list) {
      if (!(element instanceof String string) || string.isBlank()) {
        throw new IllegalStateException(
            source + ": '" + path + "' must contain only non-blank strings");
      }
      result.add(string);
    }
    return result;
  }
}
