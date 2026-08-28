package org.sterl.llmpeon.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * BDD 2a §7 (9–11): {@link ExtraBody#parse} — reserved-key stripping, invalid JSON, non-object JSON.
 */
class ExtraBodyTest {

    @Test
    void parseStripsReservedKeysKeepsRest() {
        // GIVEN a body with reserved top-level keys plus a custom one
        // WHEN ExtraBody.parse(...)
        // THEN the reserved keys are stripped and the rest is kept
        var result = ExtraBody.parse("{\"model\":\"x\",\"messages\":[],\"tools\":[],\"foo\":1}");
        assertThat(result).isEqualTo(Map.of("foo", 1));
    }

    @Test
    void parseStripsTopLevelReservedKeysOnlyNestedUntouched() {
        // GIVEN a body whose nested object contains reserved key names
        // WHEN ExtraBody.parse(...)
        // THEN only the top-level reserved keys are stripped, nested entries stay
        var result = ExtraBody.parse("{\"model\":\"x\",\"opts\":{\"tools\":[1],\"foo\":2}}");
        assertThat(result).isEqualTo(Map.of("opts", Map.of("tools", List.of(1), "foo", 2)));
    }

    @Test
    void invalidJsonWarnsAndReturnsNull() {
        // GIVEN invalid JSON
        // WHEN ExtraBody.parse(...)
        // THEN null — the warning log is not asserted, null is the contract
        assertThat(ExtraBody.parse("{invalid")).isNull();
    }

    @Test
    void blankOrNonObjectJsonReturnsNull() {
        // GIVEN blank or non-object JSON (array / scalar)
        // WHEN ExtraBody.parse(...)
        // THEN null for all of them
        assertThat(ExtraBody.parse(null)).isNull();
        assertThat(ExtraBody.parse("")).isNull();
        assertThat(ExtraBody.parse("   ")).isNull();
        assertThat(ExtraBody.parse("[1]")).isNull();
        assertThat(ExtraBody.parse("5")).isNull();
    }
}
