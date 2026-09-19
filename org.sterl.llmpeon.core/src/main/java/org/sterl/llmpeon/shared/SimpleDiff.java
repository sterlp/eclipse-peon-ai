package org.sterl.llmpeon.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generates a unified diff string from two texts using LCS (longest common subsequence).
 */
public class SimpleDiff {

    private static final int CONTEXT = 3;

    /**
     * Maximum size of the LCS matrix (rows x cols, 4 bytes per cell, ~20 MB at the limit).
     * Above this the diff is skipped with a summary instead of risking an OOM
     * (open-points 2026-09-16: a corrupted 7.3M-line file took down the whole Eclipse process).
     */
    private static final long MAX_LCS_CELLS = 5_000_000L;

    /**
     * Produces a unified diff string with compact hunks (3 lines of contextFile) suitable for diff2html rendering.
     * @return empty string if no changes; a string starting with "--- a/" if the diff was
     *         computed; otherwise a human-readable summary when the input exceeds
     *         {@link #MAX_LCS_CELLS} (diff skipped, both line counts and the limit exposed)
     */
    public static String unifiedDiff(String fileName, String oldText, String newText) {
        String[] oldLines = (oldText == null ? "" : oldText).split("\n", -1);
        String[] newLines = (newText == null ? "" : newText).split("\n", -1);

        long cells = (long) oldLines.length * newLines.length;
        if (cells > MAX_LCS_CELLS) return skippedSummary(oldLines.length, newLines.length, cells);

        List<String> diffLines = lcsDiff(oldLines, newLines);
        if (diffLines.stream().allMatch(l -> l.startsWith(" "))) return "";

        // Find indices of changed lines (non-contextFile)
        List<Integer> changeIndices = new ArrayList<>();
        for (int i = 0; i < diffLines.size(); i++) {
            if (!diffLines.get(i).startsWith(" ")) changeIndices.add(i);
        }
        if (changeIndices.isEmpty()) return "";

        // Group changes into hunks: merge if gap between changes <= 2*CONTEXT
        List<int[]> hunkRanges = new ArrayList<>(); // [startIdx, endIdx] inclusive in diffLines
        int hunkStart = Math.max(0, changeIndices.get(0) - CONTEXT);
        int hunkEnd = Math.min(diffLines.size() - 1, changeIndices.get(0) + CONTEXT);

        for (int ci = 1; ci < changeIndices.size(); ci++) {
            int nextStart = Math.max(0, changeIndices.get(ci) - CONTEXT);
            int nextEnd = Math.min(diffLines.size() - 1, changeIndices.get(ci) + CONTEXT);
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
            // Count old/new line numbers at hunk start
            int oldLineNo = 1, newLineNo = 1;
            for (int i = 0; i < range[0]; i++) {
                String l = diffLines.get(i);
                if (l.startsWith(" ") || l.startsWith("-")) oldLineNo++;
                if (l.startsWith(" ") || l.startsWith("+")) newLineNo++;
            }
            int oldCount = 0, newCount = 0;
            for (int i = range[0]; i <= range[1]; i++) {
                String l = diffLines.get(i);
                if (l.startsWith(" ") || l.startsWith("-")) oldCount++;
                if (l.startsWith(" ") || l.startsWith("+")) newCount++;
            }
            sb.append("@@ -").append(oldLineNo).append(',').append(oldCount)
              .append(" +").append(newLineNo).append(',').append(newCount).append(" @@\n");
            for (int i = range[0]; i <= range[1]; i++) {
                sb.append(diffLines.get(i)).append('\n');
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

    /**
     * Human-readable replacement for a skipped diff — exposes why it was skipped
     * (both line counts + the limit), never a lie, never a silent loss.
     */
    private static String skippedSummary(int oldLineCount, int newLineCount, long cells) {
        long bytes = cells * 4L;
        String size = bytes >= 1_000_000_000L
            ? String.format(Locale.ROOT, "%.1f GB", bytes / 1_000_000_000.0)
            : (bytes / 1_000_000) + " MB";
        return "file updated, diff skipped: " + oldLineCount + " → " + newLineCount
            + " lines (LCS matrix would be ~" + size + ", limit " + MAX_LCS_CELLS + " cells)";
    }
}
