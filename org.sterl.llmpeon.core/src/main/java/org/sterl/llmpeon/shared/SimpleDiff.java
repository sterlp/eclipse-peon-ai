package org.sterl.llmpeon.shared;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Generates a unified diff string from two texts using LCS (longest common subsequence).
 *
 * <p>Rules: R-SD-1…3, org.sterl.llmpeon.core/docs/file-update-diff.md. Only the invariants the doc
 * cannot carry live here:
 * <ul>
 * <li>identical prefix/suffix lines are trimmed away first, so only the changed region feeds the LCS
 *     matrix (standard LCS optimization) — the context regions stay virtual, they are never
 *     materialized because the prefix can be millions of lines;</li>
 * <li>{@link #MAX_CHANGED_LINES} is the single bound: 500 changed lines keep the matrix at
 *     ≤ 250×250 ints ≈ 1 MB, which is what made the old separate matrix guard redundant
 *     (open-points 2026-09-16: a corrupted 7.3M-line file took down the whole Eclipse process);</li>
 * <li>a truncated region prints no trailing suffix context — those lines would claim the real file
 *     end follows the cut directly; the marker counts the lines actually shown, never the cap
 *     constant (a tool never lies about its limit);</li>
 * <li>lines are split without a trailing empty element: a file ending in "\n" has no extra line, so a
 *     create diff never opens with a phantom removal.</li>
 * </ul>
 */
public class SimpleDiff {

    private static final int CONTEXT = 3;

    /**
     * Cap on the changed region (trimmed old + new lines) — above it the diff is truncated, not
     * skipped. Deliberately a constant, no configuration surface (doc "Offene Punkte").
     */
    private static final int MAX_CHANGED_LINES = 500;

    /** NUL sniff window; the tools hand over a String, so there is no byte layer to check. */
    private static final int BINARY_SNIFF_CHARS = 8 * 1024;

    /**
     * Produces a unified diff string with compact hunks (3 lines of contextFile) suitable for diff2html rendering.
     * @return empty string if no changes; a string starting with "--- a/" if the diff was computed
     *         (truncated with a trailing marker line above {@link #MAX_CHANGED_LINES} changed lines);
     *         otherwise a human-readable summary naming the file and both line counts
     */
    public static String unifiedDiff(String fileName, @Nullable String oldText, @Nullable String newText) {
        String[] oldLines = splitLines(oldText);
        String[] newLines = splitLines(newText);

        if (isBinary(oldText) || isBinary(newText)) {
            return skippedSummary(fileName, oldLines.length, newLines.length, "binary content");
        }

        // Trim identical head and tail — the cap and the LCS run on the trimmed region only;
        // identical inputs trim to nothing.
        int minLen = Math.min(oldLines.length, newLines.length);
        int prefix = 0;
        while (prefix < minLen && oldLines[prefix].equals(newLines[prefix])) prefix++;
        int suffix = 0;
        while (suffix < minLen - prefix
            && oldLines[oldLines.length - 1 - suffix].equals(newLines[newLines.length - 1 - suffix])) suffix++;

        String[] trimmedOld = Arrays.copyOfRange(oldLines, prefix, oldLines.length - suffix);
        String[] trimmedNew = Arrays.copyOfRange(newLines, prefix, newLines.length - suffix);
        if (trimmedOld.length == 0 && trimmedNew.length == 0) return "";

        int changedLines = trimmedOld.length + trimmedNew.length;
        boolean truncated = changedLines > MAX_CHANGED_LINES;
        // the cut region must not show the identical tail — adjacency to the real file end would lie
        int outSuffix = truncated ? 0 : suffix;
        int shownLines = changedLines;
        if (truncated) {
            int shownOld = trimmedOld.length == 0 ? 0
                : Math.clamp(Math.round((float) MAX_CHANGED_LINES * trimmedOld.length / changedLines), 1, trimmedOld.length);
            int shownNew = Math.min(trimmedNew.length, MAX_CHANGED_LINES - shownOld);
            trimmedOld = Arrays.copyOf(trimmedOld, shownOld);
            trimmedNew = Arrays.copyOf(trimmedNew, shownNew);
            shownLines = shownOld + shownNew;
        }

        // LCS on the (possibly capped) trimmed region. The full diff is prefix context + trimmed diff
        // + suffix context, all coordinates are full-diff coordinates.
        List<String> diffLines = lcsDiff(trimmedOld, trimmedNew);
        int fullEnd = prefix + diffLines.size() - 1 + outSuffix;

        // Find indices of changed lines (non-context), grouped into hunks: merge if the
        // gap between changes is <= 2*CONTEXT.
        List<Integer> changes = changeIndices(diffLines);
        int firstFull = prefix + changes.get(0);
        List<int[]> hunkRanges = new ArrayList<>(); // [startIdx, endIdx] inclusive
        int hunkStart = Math.max(0, firstFull - CONTEXT);
        int hunkEnd = Math.min(fullEnd, firstFull + CONTEXT);

        for (int ci = 1; ci < changes.size(); ci++) {
            int nextFull = prefix + changes.get(ci);
            int nextStart = Math.max(0, nextFull - CONTEXT);
            int nextEnd = Math.min(fullEnd, nextFull + CONTEXT);
            if (nextStart <= hunkEnd + 1) {
                hunkEnd = nextEnd;
            } else {
                hunkRanges.add(new int[]{hunkStart, hunkEnd});
                hunkStart = nextStart;
                hunkEnd = nextEnd;
            }
        }
        hunkRanges.add(new int[]{hunkStart, hunkEnd});

        var sb = new StringBuilder();
        sb.append("--- a/").append(fileName).append('\n');
        sb.append("+++ b/").append(fileName).append('\n');

        for (int[] range : hunkRanges) {
            int start = range[0], end = range[1];

            // Old/new line numbers at hunk start: every line before the hunk is either
            // prefix context (counts for both) or a trimmed diff line
            int oldLineNo = 1 + Math.min(start, prefix);
            int newLineNo = oldLineNo;
            if (start > prefix) {
                for (int i = 0; i < start - prefix; i++) {
                    String l = diffLines.get(i);
                    if (l.startsWith(" ") || l.startsWith("-")) oldLineNo++;
                    if (l.startsWith(" ") || l.startsWith("+")) newLineNo++;
                }
            }

            int prefixEnd = Math.min(end, prefix - 1);
            int segStart = Math.max(start, prefix);
            int segEnd = Math.min(end, prefix + diffLines.size() - 1);
            int suffixStart = Math.max(segEnd + 1, prefix + diffLines.size());

            int oldCount = Math.max(0, prefixEnd - start + 1);
            int newCount = oldCount;
            for (int k = segStart; k <= segEnd; k++) {
                String l = diffLines.get(k - prefix);
                if (l.startsWith(" ") || l.startsWith("-")) oldCount++;
                if (l.startsWith(" ") || l.startsWith("+")) newCount++;
            }
            oldCount += Math.max(0, end - suffixStart + 1);
            newCount += Math.max(0, end - suffixStart + 1);

            sb.append("@@ -").append(oldLineNo).append(',').append(oldCount)
              .append(" +").append(newLineNo).append(',').append(newCount).append(" @@\n");
            for (int k = start; k <= prefixEnd; k++) {
                sb.append(' ').append(oldLines[k]).append('\n');
            }
            for (int k = segStart; k <= segEnd; k++) {
                sb.append(diffLines.get(k - prefix)).append('\n');
            }
            for (int k = suffixStart; k <= end; k++) {
                sb.append(' ').append(newLines[newLines.length - outSuffix + k - prefix - diffLines.size()]).append('\n');
            }
        }
        if (truncated) {
            // a context line, so both renderers keep it inside the block — it must stay the last line
            sb.append(' ').append("… diff truncated — showing ")
              .append(shownLines).append(" of ").append(changedLines).append(" changed lines").append('\n');
        }
        return sb.toString();
    }

    /**
     * One line per line of the text: no trailing empty element after "\n", no element at all for an
     * absent or empty file (the shape of a create diff, R-SD-1).
     */
    private static String[] splitLines(@Nullable String text) {
        if (text == null || text.isEmpty()) return new String[0];
        String body = text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
        return body.split("\n", -1);
    }

    private static boolean isBinary(@Nullable String text) {
        if (text == null) return false;
        int end = Math.min(text.length(), BINARY_SNIFF_CHARS);
        for (int i = 0; i < end; i++) {
            if (text.charAt(i) == '\0') return true;
        }
        return false;
    }

    private static List<String> lcsDiff(String[] a, String[] b) {
        int m = a.length, n = b.length;
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (a[i - 1].equals(b[j - 1])) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i - 1][j]);
                }
            }
        }

        var result = new ArrayList<String>();
        int i = m, j = n;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && a[i - 1].equals(b[j - 1])) {
                result.add(0, " " + a[i - 1]);
                i--; j--;
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                result.add(0, "+" + b[j - 1]);
                j--;
            } else {
                result.add(0, "-" + a[i - 1]);
                i--;
            }
        }
        return result;
    }

    private static List<Integer> changeIndices(List<String> diffLines) {
        var indices = new ArrayList<Integer>();
        for (int i = 0; i < diffLines.size(); i++) {
            if (!diffLines.get(i).startsWith(" ")) indices.add(i);
        }
        return indices;
    }

    /**
     * Human-readable replacement for a change we do not diff — names the file and both line counts,
     * never a lie, never a silent loss. Both renderers show it as a plain tool line.
     */
    private static String skippedSummary(String fileName, int oldLineCount, int newLineCount, String reason) {
        return "file updated, diff skipped for " + fileName + ": " + oldLineCount + " → " + newLineCount
            + " lines (" + reason + ")";
    }
}
