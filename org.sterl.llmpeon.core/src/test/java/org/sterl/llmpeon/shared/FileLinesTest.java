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
    void testInsertZeroOrNegativePrepends() {
        assertEquals("x\na\nb", FileLines.insertLines("a\nb", 0, "x"));
        assertEquals("x\na\nb", FileLines.insertLines("a\nb", -5, "x"));
    }

    @Test
    void testInsertIntoEmptyPrepend() {
        assertEquals("x", FileLines.insertLines("", 0, "x"));
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

    @Test
    void clampsEndLineToFileEnd() {
        String content = java.util.stream.IntStream.rangeClosed(1, 120)
                .mapToObj(i -> "line " + i).collect(java.util.stream.Collectors.joining("\n"));

        String result = FileLines.extract(content, 100, 900);

        assertEquals(21, result.lines().count());
        org.junit.jupiter.api.Assertions.assertTrue(result.startsWith(" 100: line 100\n"));
        org.junit.jupiter.api.Assertions.assertTrue(result.endsWith(" 120: line 120\n"));
    }

    @Test
    void startBeyondEndReturnsHint() {
        String content = java.util.stream.IntStream.rangeClosed(1, 120)
                .mapToObj(i -> "line " + i).collect(java.util.stream.Collectors.joining("\n"));

        assertEquals("file has 120 lines, requested start 800", FileLines.extract(content, 800, 0));
    }

    @Test
    void swapsBoundsBeforeClamping() {
        String content = java.util.stream.IntStream.rangeClosed(1, 120)
                .mapToObj(i -> "line " + i).collect(java.util.stream.Collectors.joining("\n"));

        String result = FileLines.extract(content, 900, 100);

        assertEquals(21, result.lines().count());
        org.junit.jupiter.api.Assertions.assertTrue(result.startsWith(" 100: line 100\n"));
        org.junit.jupiter.api.Assertions.assertTrue(result.endsWith(" 120: line 120\n"));
    }


    @Test
    void existingBehaviourUnchanged() {
        String content = "alpha\nbeta\ngamma";

        assertEquals(content, FileLines.extract(content, 0, 0));
        assertEquals("   2: beta\n   3: gamma\n", FileLines.extract(content, 3, 2));
        assertEquals("   2: beta\n", FileLines.extract(content, 2, 2));
    }

    @Test
    void testTailShorterThanRequested() {
        assertEquals("a\nb", FileLines.tail("a\nb", 5));
    }

    @Test
    void testTailExactMatch() {
        assertEquals("a\nb\nc", FileLines.tail("a\nb\nc", 3));
    }

    @Test
    void testTailLongerThanRequested() {
        assertEquals("c\nd\ne", FileLines.tail("a\nb\nc\nd\ne", 3));
    }
}
