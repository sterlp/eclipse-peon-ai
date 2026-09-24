package org.sterl.llmpeon.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SimpleDiffTest {

    @Test
    void smallInput_unifiedDiffUnchanged() {
        // GIVEN a small 3-line file with one changed line
        // WHEN
        String diff = SimpleDiff.unifiedDiff("a.txt", "a\nb\nc", "a\nB\nc");
        // THEN the exact unified diff — the guard must not change the normal path
        assertEquals("--- a/a.txt\n+++ b/a.txt\n@@ -1,3 +1,3 @@\n a\n-b\n+B\n c\n", diff);
    }

    @Test
    void oneLineChangeInBigFile_realDiff() {
        // GIVEN 2500 lines with one middle line changed — 2500x2500 = 6.25M cells was
        // skipped before trimming; trimming leaves 1x1
        // WHEN
        String diff = SimpleDiff.unifiedDiff("big.txt", lines(2500), lines(2500, 1250, "changed-1250"));
        // THEN a real diff for the changed line, no skip summary
        assertTrue(diff.startsWith("--- a/"));
        assertTrue(diff.contains("-line1250"));
        assertTrue(diff.contains("+changed-1250"));
        assertFalse(diff.contains("diff skipped"));
    }

    @Test
    void skipSummary_namesFile() {
        // GIVEN over-limit input (full rewrite, nothing to trim away)
        // WHEN
        String diff = SimpleDiff.unifiedDiff("big.txt", prefixedLines(1500, "old-"), prefixedLines(1500, "new-"));
        // THEN the skip summary names the file
        assertTrue(diff.contains("diff skipped for big.txt"));
    }

    @Test
    void sharedPrefix_trimmed_realDiff() {
        // GIVEN 2500 x 2000 sharing a 2000-line prefix — trimming leaves 500 x 0, so the
        // matrix-size guard is no longer the deciding factor for this shape
        // WHEN
        String diff = SimpleDiff.unifiedDiff("big.txt", lines(2500), lines(2000));
        // THEN a real diff, not a skip summary
        assertTrue(diff.startsWith("--- a/"));
        assertFalse(diff.contains("diff skipped"));
    }

    @Test
    void guard_aboveThreshold_returnsSummary() {
        // GIVEN 1500 x 1500 = 2,250,000 cells with a full rewrite (nothing to trim) —
        // over the 1,250,000-cell limit
        // WHEN
        String diff = SimpleDiff.unifiedDiff("big.txt", prefixedLines(1500, "old-"), prefixedLines(1500, "new-"));
        // THEN no diff, summary exposes the file name, both line counts and the cells limit
        assertFalse(diff.startsWith("--- a/"));
        assertTrue(diff.contains("diff skipped for big.txt"));
        assertTrue(diff.contains("1500 → 1500 lines"));
        assertTrue(diff.contains("limit 1250000 cells"));
    }

    @Test
    void guard_crashShape_returnsSummary() {
        // GIVEN the crash shape in memory-sparing scale: 200,000 old x 100 new lines sharing
        // a 100-line prefix — trimming bounds the matrix (0 cells) but the changed region
        // still spans 199,900 lines, over the 100,000-line cap
        // WHEN
        String diff = SimpleDiff.unifiedDiff("huge.txt", lines(200_000), lines(100));
        // THEN summary naming the file, both line counts and the changed-lines cap
        assertFalse(diff.startsWith("--- a/"));
        assertTrue(diff.contains("diff skipped for huge.txt"));
        assertTrue(diff.contains("200000 → 100 lines"));
        assertTrue(diff.contains("changed region spans 199900 lines, cap 100000"));
    }

    @Test
    void identicalInputs_emptyDiff() {
        // GIVEN identical inputs — prefix/suffix trimming leaves nothing
        // WHEN
        String diff = SimpleDiff.unifiedDiff("a.txt", lines(500), lines(500));
        // THEN exactly empty, as before
        assertEquals("", diff);
    }

    private static String lines(int count) {
        return lines(count, -1, null);
    }

    private static String lines(int count, int changedIndex, String replacement) {
        var sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append('\n');
            sb.append(i == changedIndex ? replacement : "line" + i);
        }
        return sb.toString();
    }

    private static String prefixedLines(int count, String prefix) {
        var sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append('\n');
            sb.append(prefix).append(i);
        }
        return sb.toString();
    }
}
