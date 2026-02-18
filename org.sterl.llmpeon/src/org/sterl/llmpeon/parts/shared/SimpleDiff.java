package org.sterl.llmpeon.parts.shared;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates a unified diff string from two texts using LCS (longest common subsequence).
 */
public class SimpleDiff {

    private static final int CONTEXT = 3;

    /**
     * Produces a unified diff string with compact hunks (3 lines of contextFile) suitable for diff2html rendering.
     * @return empty string if no changes
     */
    public static String unifiedDiff(String fileName, String oldText, String newText) {
        String[] oldLines = (oldText == null ? "" : oldText).split("\n", -1);
        String[] newLines = (newText == null ? "" : newText).split("\n", -1);

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
}
