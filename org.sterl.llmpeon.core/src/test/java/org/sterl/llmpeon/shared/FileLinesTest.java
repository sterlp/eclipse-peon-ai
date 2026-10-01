package org.sterl.llmpeon.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    // ------------------------------------------------------------------ replaceLines: out-of-range → honest error (Paul 2026-09-30)
    // The old silent high-clamp replaced the LAST line for a stale line number; now a bound
    // beyond the file fails with the actual line count + requested range.

    @Test
    void replaceLines_startBeyondEnd_throwsWithLineCountAndRange() {
        // GIVEN a 3-line content
        String content = "a\nb\nc";

        // WHEN the requested start is beyond the end
        assertThatThrownBy(() -> FileLines.replaceLines(content, 5, 5, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("file has 3 lines")
                .hasMessageContaining("5-5");
    }

    @Test
    void replaceLines_endBeyondEnd_throwsWithLineCountAndRange() {
        // GIVEN a 3-line content
        String content = "a\nb\nc";

        // WHEN the requested end is beyond the file
        assertThatThrownBy(() -> FileLines.replaceLines(content, 1, 9, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("file has 3 lines")
                .hasMessageContaining("1-9");
    }

    @Test
    void replaceLines_lastLineInRange_replaces() {
        // GIVEN a 3-line content
        String content = "a\nb\nc";

        // WHEN the last line is replaced — endLine = total is valid
        assertThat(FileLines.replaceLines(content, 3, 3, "X")).isEqualTo("a\nb\nX");
    }

    @Test
    void replaceLines_swappedInValidRange_usesSwappedRange() {
        // GIVEN a 3-line content
        String content = "a\nb\nc";

        // WHEN start > end within the file — the range is swapped to 1-3
        assertThat(FileLines.replaceLines(content, 3, 1, "X")).isEqualTo("X");
    }

    @Test
    void replaceLines_swappedBeyondEnd_throws() {
        // GIVEN a 3-line content
        String content = "a\nb\nc";

        // WHEN start > end and the swapped range exceeds the file
        assertThatThrownBy(() -> FileLines.replaceLines(content, 5, 2, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("file has 3 lines");
    }

    @Test
    void replaceLines_emptyFileLine1_becomesReplacement() {
        // GIVEN an empty file (= 1 line, same split convention as extract/countLines)

        // WHEN line 1 is replaced
        assertThat(FileLines.replaceLines("", 1, 1, "x")).isEqualTo("x");
    }

    @Test
    void replaceLines_emptyFileBeyondLine1_throws() {
        // GIVEN an empty file (= 1 line)

        // WHEN line 2 is requested
        assertThatThrownBy(() -> FileLines.replaceLines("", 2, 2, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("file has 1 lines")
                .hasMessageContaining("2-2");
    }

    @Test
    void replaceLines_zeroSentinels_replaceWholeFile() {
        // GIVEN a 2-line content
        String content = "a\nb";

        // WHEN the 0/0 sentinel is used — documented whole-file replace, unchanged behaviour
        assertThat(FileLines.replaceLines(content, 0, 0, "x")).isEqualTo("x");
    }



    @Test
    void extractWholeFileHasLineNumbers() {
        // R9
        String content = "alpha\nbeta\ngamma";
        String whole = FileLines.extract(content, 0, 0);

        // 1-based, identical format to a range read
        assertEquals("   1: alpha\n   2: beta\n   3: gamma\n", whole);
        assertEquals(FileLines.extract(content, 1, 3), whole);
    }

    @Test
    void countLinesCountsDominantEndingLines() {
        assertEquals(0, FileLines.countLines(null));
        assertEquals(1, FileLines.countLines("only"));
        assertEquals(3, FileLines.countLines("a\nb\nc"));
        // trailing newline yields an empty last line — same split as extract()
        assertEquals(4, FileLines.countLines("a\nb\nc\n"));
        assertEquals(2, FileLines.countLines("a\r\nb"));
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
