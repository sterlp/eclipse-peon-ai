package org.sterl.llmpeon.shared;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Generates a unified diff string from two texts using LCS (longest common subsequence).
 *
 * <p>Two guards keep the diff honest and bounded (open-points 2026-09-16: a corrupted
 * 7.3M-line file took down the whole Eclipse process):
 * <ul>
 * <li>identical prefix/suffix lines are trimmed away first, so only the changed region
 *     feeds the LCS matrix (standard LCS optimization);</li>
 * <li>{@link #MAX_LCS_CELLS} bounds the matrix size on the trimmed arrays — above it the
 *     diff is skipped with a summary;</li>
 * <li>{@link #MAX_DIFF_LINES} bounds the changed region itself (trimmed old + new lines) —
 *     trimming bounds the matrix, not the diff output, so a 7.3M-line file vs a 100-line
 *     file sharing the 100-line prefix trims to 0 cells but would still build a 7M-line
 *     result; above the cap the diff is skipped with a summary naming this bound.</li>
 * </ul>
 */
public class SimpleDiff {

    private static final int CONTEXT = 3;

    /**
     * Maximum size of the LCS matrix (rows x cols, 4 bytes per cell, ~5 MB at the limit),
     * computed on the prefix/suffix-trimmed line arrays. Above this the diff is skipped
     * with a summary instead of risking an OOM
     * (open-points 2026-09-16: a corrupted 7.3M-line file took down the whole Eclipse process).
     */
    private static final long MAX_LCS_CELLS = 1_250_000L;

    /**
     * Maximum total number of lines in the trimmed (changed) region — trimmed old + new.
     * Trimming bounds the matrix, not the diff output, so this second guard keeps the
     * OOM protection honest (see class javadoc).
     */
    private static final int MAX_DIFF_LINES = 100_000;

    /**
     * Produces a unified diff string with compact hunks (3 lines of contextFile) suitable for diff2html rendering.
     * @return empty string if no changes; a string starting with "--- a/" if the diff was
     *         computed; otherwise a human-readable summary naming the file when the trimmed
     *         input exceeds {@link #MAX_LCS_CELLS} or the changed region exceeds
     *         {@link #MAX_DIFF_LINES} (diff skipped, both line counts and the bound exposed)
     */
    public static String unifiedDiff(String fileName, String oldText, String newText) {
        String[] oldLines = (oldText == null ? "" : oldText).split("\n", -1);
        String[] newLines = (newText == null ? "" : newText).split("\n", -1);

        // Trim identical head and tail (standard LCS optimization) — the guards and the LCS
        // run on the trimmed region only; identical inputs trim to nothing.
        int minLen = Math.min(oldLines.length, newLines.length);
        int prefix = 0;
        while (prefix < minLen && oldLines[prefix].equals(newLines[prefix])) prefix++;
        int suffix = 0;
        while (suffix < minLen - prefix
            && oldLines[oldLines.length - 1 - suffix].equals(newLines[newLines.length - 1 - suffix])) suffix++;

        String[] trimmedOld = Arrays.copyOfRange(oldLines, prefix, oldLines.length - suffix);
        String[] trimmedNew = Arrays.copyOfRange(newLines, prefix, newLines.length - suffix);
        if (trimmedOld.length == 0 && trimmedNew.length == 0) return "";

        long cells = (long) trimmedOld.length * trimmedNew.length;
        if (cells > MAX_LCS_CELLS) {
            long bytes = cells * 4L;
            String size = bytes >= 1_000_000_000L
                ? String.format(Locale.ROOT, "%.1f GB", bytes / 1_000_000_000.0)
                : (bytes / 1_000_000) + " MB";
            return skippedSummary(fileName, oldLines.length, newLines.length,
                "LCS matrix would be ~" + size + ", limit " + MAX_LCS_CELLS + " cells");
        }
        int changedLines = trimmedOld.length + trimmedNew.length;
        if (changedLines > MAX_DIFF_LINES) {
            return skippedSummary(fileName, oldLines.length, newLines.length,
                "changed region spans " + changedLines + " lines, cap " + MAX_DIFF_LINES);
        }

        // LCS on the trimmed region only. The full diff is prefix context + trimmed diff
        // + suffix context; the context regions stay virtual (never materialized — the
        // prefix can be millions of lines).
        List<String> diffLines = lcsDiff(trimmedOld, trimmedNew);
        int fullEnd = prefix + diffLines.size() - 1 + suffix;

        // Find indices of changed lines (non-context), grouped into hunks: merge if the
        // gap between changes is <= 2*CONTEXT. All coordinates are full-diff coordinates.
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
                // merge
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
                sb.append(' ').append(newLines[newLines.length - suffix + k - prefix - diffLines.size()]).append('\n');
            }
        }
        return sb.toString();
    }

    private static List<String> lcsDiff(String[] a, String[] b) {
        int m = a.length, n = b.length;
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (a[i - 1].equals(b[j - 1])) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
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
     * Human-readable replacement for a skipped diff — names the file and exposes why it was
     * skipped (both line counts + the bound that hit), never a lie, never a silent loss.
     */
    private static String skippedSummary(String fileName, int oldLineCount, int newLineCount, String reason) {
        return "file updated, diff skipped for " + fileName + ": " + oldLineCount + " → " + newLineCount
            + " lines (" + reason + ")";
    }
}
