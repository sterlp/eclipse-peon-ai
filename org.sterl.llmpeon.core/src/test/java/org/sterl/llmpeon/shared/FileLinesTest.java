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

    @Test
    void testInsertAfterLine() {
        // GIVEN
        String content = "a\nb\nc";
        // WHEN
        String result = FileLines.insertLines(content, 2, "x\ny");
        // THEN
        assertEquals("a\nb\nx\ny\nc", result);
    }

    @Test
    void testInsertAfterFirstLine() {
        assertEquals("a\nx\nb", FileLines.insertLines("a\nb", 1, "x"));
    }

    @Test
    void testInsertNullAfterLineAppendsAtEnd() {
        assertEquals("a\nb\nx", FileLines.insertLines("a\nb", null, "x"));
    }

    @Test
    void testInsertZeroOrNegativeAppendsAtEnd() {
        assertEquals("a\nb\nx", FileLines.insertLines("a\nb", 0, "x"));
        assertEquals("a\nb\nx", FileLines.insertLines("a\nb", -5, "x"));
    }

    @Test
    void testInsertBeyondEndAppendsAtEnd() {
        assertEquals("a\nb\nx", FileLines.insertLines("a\nb", 99, "x"));
    }

    @Test
    void testInsertIntoEmptyContent() {
        assertEquals("x", FileLines.insertLines("", 3, "x"));
        assertEquals("x", FileLines.insertLines(null, 3, "x"));
    }

    @Test
    void testInsertEmptyContentReturnsOriginal() {
        assertEquals("a\nb", FileLines.insertLines("a\nb", 1, ""));
        assertEquals("a\nb", FileLines.insertLines("a\nb", 1, null));
    }

    @Test
    void testInsertPreservesCrlf() {
        assertEquals("a\r\nx\r\nb", FileLines.insertLines("a\r\nb", 1, "x"));
    }
}
