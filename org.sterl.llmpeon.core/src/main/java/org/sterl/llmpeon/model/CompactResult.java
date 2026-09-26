package org.sterl.llmpeon.model;

import org.sterl.llmpeon.shared.StringUtil;

/**
 * Outcome of a compact attempt (R-CIB-6, Da-Dok F9): the status plus the numbers that back it.
 * The two legacy "false" cases stay fundamentally different (R-CC-3, ADR-0055).
 *
 * @param status the outcome
 * @param stats  the numbers (estimate before/after, stage, dropped chars, result size, model,
 *               duration); {@code null} for {@link Status#SKIPPED_SMALL} (nothing ran)
 * @param cause  human-readable failure cause for {@link Status#FAILED_EMPTY}
 */
public record CompactResult(Status status, Stats stats, String cause) {

    public enum Status { COMPACTED, SKIPPED_SMALL, FAILED_EMPTY }

    /** The deepest truncation stage that was applied (R-CIB-4). */
    public enum Stage { NONE, THINK_AND_USER, TOOL_RESULTS, PER_MESSAGE }

    public record Stats(int messageCount, int estimateBefore, int estimateAfter, Stage stage,
                        long droppedChars, int resultChars, String model, long millis) {}

    /** The compact succeeded — the memory is already reset and re-seeded with the summary. */
    public static CompactResult compacted(Stats stats) {
        return new CompactResult(Status.COMPACTED, stats, null);
    }

    /** The memory is too small to compact — legitimate, not an error, memory untouched. */
    public static CompactResult skippedSmall() {
        return new CompactResult(Status.SKIPPED_SMALL, null, null);
    }

    /** The compressor returned no usable summary — an error, memory untouched. */
    public static CompactResult failedEmpty(Stats stats, String cause) {
        return new CompactResult(Status.FAILED_EMPTY, stats, cause);
    }

    /**
     * One line, two recipients (R-CIB-6): the log AND the tool result to the agent carry the same
     * numbers, so the LLM understands in the next turn what happened.
     */
    public String resultLine() {
        return switch (status) {
            case COMPACTED -> "compressed " + stats.messageCount() + " messages ~" + StringUtil.toK(stats.estimateBefore())
                    + " → input ~" + StringUtil.toK(stats.estimateAfter())
                    + ", result " + StringUtil.toK(stats.resultChars())
                    + ", stage: " + stageDescription(stats.stage());
            case SKIPPED_SMALL -> "compact skipped: context too small";
            case FAILED_EMPTY -> "compact failed: " + cause;
        };
    }

    private static String stageDescription(Stage stage) {
        return switch (stage) {
            case NONE -> "none";
            case THINK_AND_USER -> "thinking 9000 (front) + last user message";
            case TOOL_RESULTS -> "tool results 6000";
            case PER_MESSAGE -> "per-message cap";
        };
    }
}
