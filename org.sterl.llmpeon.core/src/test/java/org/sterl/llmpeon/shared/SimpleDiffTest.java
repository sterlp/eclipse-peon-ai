package org.sterl.llmpeon.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SimpleDiffTest {

    /** The contract of a truncated diff — context line, so it stays inside the rendered block. */
    private static final String TRUNCATION_MARKER = " … diff truncated — showing ";
    /** Written as a char, never as an escape: the source file must stay free of a raw NUL byte. */
    private static final char NUL = (char) 0;

    @Test
    void smallInput_unifiedDiffUnchanged() {
        // GIVEN a small 3-line file with one changed line
        // WHEN
        String diff = SimpleDiff.unifiedDiff("a.txt", "a\nb\nc", "a\nB\nc");
        // THEN the exact unified diff — the cap must not change the normal path
        assertEquals("--- a/a.txt\n+++ b/a.txt\n@@ -1,3 +1,3 @@\n a\n-b\n+B\n c\n", diff);
    }

    @Test
    void oneLineChangeInBigFile_realDiff() {
        // GIVEN 2500 lines with one middle line changed — 2500x2500 was skipped before trimming;
        // trimming leaves 1x1
        // WHEN
        String diff = SimpleDiff.unifiedDiff("big.txt", lines(2500), lines(2500, 1250, "changed-1250"));
        // THEN a real diff for the changed line, no skip summary, no truncation
        assertTrue(diff.startsWith("--- a/"));
        assertTrue(diff.contains("-line1250"));
        assertTrue(diff.contains("+changed-1250"));
        assertFalse(diff.contains("diff skipped"));
    }

    // UC-SD-2
    @Test
    void sharedPrefix_trimmed_realDiff() {
        // GIVEN 2500 x 2000 sharing a 2000-line prefix — trimming leaves 500 x 0, exactly the cap,
        // so this shape is still a complete diff
        // WHEN
        String diff = SimpleDiff.unifiedDiff("big.txt", lines(2500), lines(2000));
        // THEN a real diff at full length — no skip summary, no marker at exactly 500 changed lines
        assertTrue(diff.startsWith("--- a/"));
        assertFalse(diff.contains("diff skipped"));
        assertFalse(diff.contains(TRUNCATION_MARKER));
    }

    @Test
    void identicalInputs_emptyDiff() {
        // GIVEN identical inputs — prefix/suffix trimming leaves nothing
        // WHEN
        String diff = SimpleDiff.unifiedDiff("a.txt", lines(500), lines(500));
        // THEN exactly empty, as before
        assertEquals("", diff);
    }

    // UC-SD-2
    @Test
    void oversizedChangedRegionGetsAPartialDiff() {
        // GIVEN a full rewrite of 1500 lines with nothing to trim away — 3000 changed lines
        // WHEN
        String diff = SimpleDiff.unifiedDiff("big.txt", prefixedLines(1500, "old-"), prefixedLines(1500, "new-"));
        // THEN a real diff instead of a summary, capped and closed by the marker naming the whole
        // change; both sides keep their proportional share of the 500
        assertTrue(diff.startsWith("--- a/"));
        assertFalse(diff.contains("diff skipped"));
        assertTrue(diff.endsWith(TRUNCATION_MARKER + "500 of 3000 changed lines\n"));
        assertEquals(250, countChanged(diff, "-"));
        assertEquals(250, countChanged(diff, "+"));
    }

    // UC-SD-2
    @Test
    void crashShapeGetsAPartialDiff() {
        // GIVEN the crash shape of open-points 2026-09-16 in memory-sparing scale: 200,000 old lines
        // sharing a 100-line prefix with 100 new lines — the changed region spans 199,900 lines
        // WHEN
        String diff = SimpleDiff.unifiedDiff("huge.txt", lines(200_000), lines(100));
        // THEN a bounded diff of the removal with the marker naming all 199,900 — the shape that
        // used to answer with a summary and showed the user nothing
        assertTrue(diff.startsWith("--- a/"));
        assertFalse(diff.contains("diff skipped"));
        assertTrue(diff.endsWith(TRUNCATION_MARKER + "500 of 199900 changed lines\n"));
        assertEquals(500, countChanged(diff, "-"));
        assertEquals(0, countChanged(diff, "+"));
    }

    // UC-SD-1, UC-SD-2
    @Test
    void createdOversizedFileIsTruncatedToTheFirst500Lines() {
        // GIVEN a create — no old content at all — of 600 lines
        // WHEN
        String diff = SimpleDiff.unifiedDiff("new.txt", "", prefixedLines(600, "line"));
        // THEN the header says "no old lines", the block carries exactly 500 additions and the
        // marker names all 600: an absent old side must not claim one of the 500 lines
        assertTrue(diff.contains("@@ -1,0 +1,500 @@\n"));
        assertEquals(500, countChanged(diff, "+"));
        assertEquals(0, countChanged(diff, "-"));
        assertTrue(diff.endsWith(TRUNCATION_MARKER + "500 of 600 changed lines\n"));
    }

    // UC-SD-2
    @Test
    void truncationDropsTheSuffixContext() {
        // GIVEN 800 changed lines in front of 100 identical trailing lines — normally a context block
        // WHEN the region is capped
        String diff = SimpleDiff.unifiedDiff("tail.txt",
            changedThenShared("old-", 800, 100), changedThenShared("new-", 800, 100));
        // THEN the marker is the last line and the identical tail is gone: printing it would claim
        // the real file end follows the cut directly
        assertTrue(diff.endsWith(TRUNCATION_MARKER + "500 of 1600 changed lines\n"));
        assertFalse(diff.contains("same"));
    }

    // UC-SD-2
    @Test
    void truncationMarkerCountsTheShownLines() {
        // GIVEN 999 changed lines per side between an identical head and tail — 1998 changed lines,
        // split proportionally to 250 + 250
        // WHEN
        String diff = SimpleDiff.unifiedDiff("both.txt", aroundShared("old-", 999), aroundShared("new-", 999));
        // THEN the marker names the count actually shown, measured on the block itself — never the
        // cap value, which would be a coincidence and a lie as soon as the split shows less
        long shown = countChanged(diff, "-") + countChanged(diff, "+");
        assertEquals(250, countChanged(diff, "-"));
        assertEquals(250, countChanged(diff, "+"));
        assertTrue(diff.endsWith(TRUNCATION_MARKER + shown + " of 1998 changed lines\n"), diff);
    }


    // UC-SD-1
    @Test
    void createdFileIsAPurePlusDiff() {
        // GIVEN no old content and two new lines, the last one terminated by "\n"
        // WHEN
        String diff = SimpleDiff.unifiedDiff("f", "", "a\nb\n");
        // THEN exactly the two additions — no phantom removal line, no invented empty context line
        assertEquals("--- a/f\n+++ b/f\n@@ -1,0 +1,2 @@\n+a\n+b\n", diff);
    }

    // UC-SD-3
    @Test
    void binaryContentGetsASummary() {
        // GIVEN a NUL byte in the old content, then one in the new content
        // WHEN
        String binaryOld = SimpleDiff.unifiedDiff("img.bin", "a\n" + NUL + "binary\n", "a\nb\n");
        String binaryNew = SimpleDiff.unifiedDiff("img.bin", "a\nb\n", "a\n" + NUL + "binary\n");
        // THEN both answer with the summary, never with a diff — no UTF-8 garbage reaches a renderer
        for (String summary : new String[] { binaryOld, binaryNew }) {
            assertFalse(summary.startsWith("--- a/"), summary);
            assertTrue(summary.contains("diff skipped for img.bin"), summary);
            assertTrue(summary.contains("binary content"), summary);
        }
    }

    // UC-SD-3
    @Test
    void nulBeyondTheSniffWindowIsPlainText() {
        // GIVEN the NUL byte at index 8192 — one char outside the 8 KB sniff window
        // WHEN
        String diff = SimpleDiff.unifiedDiff("nul.txt",
            "x".repeat(8192) + NUL + "old", "x".repeat(8192) + NUL + "new");
        // THEN a real diff: the window decides, not the whole text
        assertTrue(diff.startsWith("--- a/"));
        assertFalse(diff.contains("diff skipped"));
    }

    /** Diff lines of one side, the file headers excluded. */
    private static long countChanged(String diff, String sign) {
        return diff.lines()
            .filter(l -> l.startsWith(sign) && !l.startsWith("--- ") && !l.startsWith("+++ "))
            .count();
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

    private static String changedThenShared(String prefix, int changed, int shared) {
        var sb = new StringBuilder();
        for (int i = 0; i < changed; i++) {
            if (i > 0) sb.append('\n');
            sb.append(prefix).append(i);
        }
        for (int i = 0; i < shared; i++) {
            sb.append('\n').append("same").append(i);
        }
        return sb.toString();
    }

    /** One shared head line, `changed` lines of one side, one shared tail line. */
    private static String aroundShared(String prefix, int changed) {
        var sb = new StringBuilder("head");
        for (int i = 0; i < changed; i++) {
            sb.append('\n').append(prefix).append(i);
        }
        return sb.append('\n').append("tail").toString();
    }
}
