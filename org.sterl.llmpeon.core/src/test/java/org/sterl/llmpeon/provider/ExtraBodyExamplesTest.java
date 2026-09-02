package org.sterl.llmpeon.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Extra-body examples (2c D1): the paste-ready snippets are user-facing, so each must be a valid,
 * non-empty JSON object without reserved top-level keys — {@link ExtraBody#parse} accepts it
 * unchanged and the pasted body does exactly what the user expects.
 */
class ExtraBodyExamplesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static Stream<Arguments> examples() {
        return ExtraBodyExamples.all().stream().map(Arguments::of);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("examples")
    void examplesAreValidNonEmptyExtraBodies(ExtraBodyExamples.Example example) throws Exception {
        // GIVEN one example
        // WHEN the raw JSON is parsed
        var raw = MAPPER.readValue(example.json(), new TypeReference<Map<String, Object>>() {});
        // THEN it is a non-empty JSON object without reserved top-level keys
        assertThat(raw)
                .as("example %s must be a non-empty JSON object", example.name())
                .isNotEmpty();
        assertThat(raw.keySet())
                .as("example %s must not use reserved keys (they would be silently stripped)", example.name())
                .doesNotContain("model", "messages", "tools");
        // AND ExtraBody.parse accepts it (non-null)
        assertThat(ExtraBody.parse(example.json())).isNotNull();
        // AND description is not blank (tooltip content is user-facing)
        assertThat(example.description())
                .as("example %s must have a non-blank description", example.name())
                .isNotBlank();
    }
}
