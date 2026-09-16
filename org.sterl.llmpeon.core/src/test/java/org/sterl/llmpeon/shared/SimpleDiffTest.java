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
    void guard_exactlyAtThreshold_computesDiff() {
        // GIVEN 2500 x 2000 = 5,000,000 cells — exactly at the limit
        // WHEN
        String diff = SimpleDiff.unifiedDiff("big.txt", lines(2500), lines(2000));
        // THEN LCS still runs at the limit
        assertTrue(diff.startsWith("--- a/"));
    }

    @Test
    void guard_aboveThreshold_returnsSummary() {
        // GIVEN 2501 x 2000 = 5,002,000 cells — over the limit
        // WHEN
        String diff = SimpleDiff.unifiedDiff("big.txt", lines(2501), lines(2000));
        // THEN no diff, summary exposes both line counts and the skip
        assertFalse(diff.startsWith("--- a/"));
        assertTrue(diff.contains("diff skipped"));
        assertTrue(diff.contains("2501"));
        assertTrue(diff.contains("2000"));
    }

    @Test
    void guard_crashShape_returnsSummary() {
        // GIVEN the crash shape in memory-sparing scale: 200,000 old x 100 new lines = 20 mio cells
        // WHEN
        String diff = SimpleDiff.unifiedDiff("huge.txt", lines(200_000), lines(100));
        // THEN summary instead of OOM, both line counts exposed
        assertFalse(diff.startsWith("--- a/"));
        assertTrue(diff.contains("diff skipped"));
        assertTrue(diff.contains("200000 → 100 lines"));
    }

    private static String lines(int count) {
        var sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append('\n');
            sb.append("line").append(i);
        }
        return sb.toString();
    }
}
