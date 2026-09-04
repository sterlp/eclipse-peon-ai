package org.sterl.llmpeon.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class RegexUtilsTest {

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
    void globToPattern_matchesDocsAtDepth() {
        var p = RegexUtils.globToPattern("*/docs/*");
        assertTrue(p.matcher("MyProject/docs/feature.md").matches());
        assertTrue(p.matcher("a/b/docs/x/y.md").matches());
        assertFalse(p.matcher("src/main/Foo.java").matches());
    }

    @Test
    void globToPattern_matchesMarkdownAnywhere() {
        var p = RegexUtils.globToPattern("*.md");
        assertTrue(p.matcher("docs/feature.md").matches());
        assertTrue(p.matcher("README.md").matches());
        assertFalse(p.matcher("docs/notes.txt").matches());
    }

    @Test
    void globToPattern_isCachedPerGlob() {
        assertSame(RegexUtils.globToPattern("*.md"), RegexUtils.globToPattern("*.md"));
    }
}
