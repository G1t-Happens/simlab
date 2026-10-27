package dev.simlab.server.lesson;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the YAML in which lessons declare their symbols and set up their experiments into records.
 *
 * <p>SnakeYAML parses the YAML into maps, lists and scalars as it does for the Spring Boot configuration, and
 * Jackson maps them to records like it does for JSON. Jackson's own YAML module would let the server answer
 * every request in YAML as well
 */
final class YamlReader {

    /**
     * Rejects misspelled properties, which would otherwise get lost without a trace. Lesson blocks, which JSON
     * writes with their kind, are read without it as YAML gives an experiment its kind by its fence
     */
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(MapperFeature.REQUIRE_TYPE_ID_FOR_SUBTYPES)
            .build();

    private YamlReader() {}

    /**
     * Reads a YAML document into a record
     *
     * @throws IllegalArgumentException if the text is empty, no YAML, or doesn't fit the record
     */
    static <T> T read(String yaml, Class<T> type) {
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        try {
            Object document = new Yaml(new SafeConstructor(options)).load(yaml);
            if (document == null) {
                throw new IllegalArgumentException("The YAML is empty");
            }
            return MAPPER.convertValue(document, type);
        } catch (YAMLException | JacksonException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }
}
