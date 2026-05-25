package org.sterl.llmpeon.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class RegexUtilsTest {

    @Test
    void testIsRegexPattern() {
        assertTrue(RegexUtils.isRegexPattern("class.*Tool"));
        assertTrue(RegexUtils.isRegexPattern("foo|bar"));
        assertTrue(RegexUtils.isRegexPattern("foo+bar"));
        assertTrue(RegexUtils.isRegexPattern("^start"));
        assertTrue(RegexUtils.isRegexPattern("end$"));
        assertFalse(RegexUtils.isRegexPattern("test_grep_workspace"));
        assertFalse(RegexUtils.isRegexPattern("simpleText"));
        assertFalse(RegexUtils.isRegexPattern("test()"));
    }

    @Test
    void testCountOccurrencesLiteral() {
        String content = "public class Foo\npublic class Bar\nprivate class Baz";
        assertEquals(3, RegexUtils.countOccurrences(content, "class"));
        assertEquals(1, RegexUtils.countOccurrences(content, "Foo"));
        assertEquals(0, RegexUtils.countOccurrences(content, "Qux"));
    }

    @Test
    void testCountOccurrencesRegex() {
        String content = "public class FooTool\npublic class BarService\nprivate class BazTool";
        assertEquals(2, RegexUtils.countOccurrences(content, "class.*Tool"));
        assertEquals(2, RegexUtils.countOccurrences(content, "FooTool|BazTool"));
        assertEquals(0, RegexUtils.countOccurrences(content, "class.*Qux"));
    }

    @Test
    void testCountOccurrencesAlternation() {
        String content = "onThinkToggle()\nthinkEnabledPreference\nPREF_ENABLED";
        assertEquals(3, RegexUtils.countOccurrences(content, "onThinkToggle|thinkEnabledPreference|PREF_"));
    }

    @Test
    void testCountOccurrencesInvalidRegex() {
        assertEquals(0, RegexUtils.countOccurrences("some text", "[invalid"));
    }
}
