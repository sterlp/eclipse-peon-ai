package org.sterl.llmpeon.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FileLinesTest {

    @Test
    void testFormatWithStartLine() {
        String content = "line1\nline2\nline3";
        String result = FileLines.format(content, 5);
        assertEquals("   5: line1\n   6: line2\n   7: line3\n", result);
    }

    @Test
    void testFormatWithStartLineZero() {
        String content = "a\nb";
        String result = FileLines.format(content, 0);
        assertEquals("   0: a\n   1: b\n", result);
    }

    @Test
    void testFormatDefaultStartLine() {
        String content = "line1\nline2";
        String result = FileLines.format(content);
        assertEquals("   1: line1\n   2: line2\n", result);
    }

    @Test
    void testFormatNullContent() {
        assertEquals("", FileLines.format(null));
        assertEquals("", FileLines.format(null, 5));
    }

    @Test
    void testFormatWithLargeLineNumbers() {
        String content = "line1\nline2";
        String result = FileLines.format(content, 999);
        assertEquals(" 999: line1\n1000: line2\n", result);
    }
}
