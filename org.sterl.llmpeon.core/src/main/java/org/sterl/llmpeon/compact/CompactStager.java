package org.sterl.llmpeon.compact;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.sterl.llmpeon.queuedmessages.UserMessageQueue;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.shared.ChatMessageUtil.RenderOptions;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.ToolService;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * Pure compact input staging (R-CIB-4): (memory snapshot, budget) → (input, disclosure, stats).
 * No agent/memory/config types — a message list plus an int, testable without mocks.
 *
 * <p>Pipeline: filter SystemMessages on list level → render uncapped → dedup on the UNCAPPED
 * renders (LinkedHashSet, keep-first, before any caps — R-CIB-2) → estimate (chars×2/7, R-CIB-1)
 * → stage 1 (thinking 9000 front-cap, only the last real user message full) → re-estimate →
 * stage 2 (tool results/arguments + thinking 6000) → re-estimate → final stage (per-message cap
 * = restTokens×7/2/n, head-keep, termination guaranteed). The disclosure ("session truncated" +
 * one caps line) is appended once at the input end, only when something was truncated —
 * including a dedup-only collapse (F4, R-CIB-5).</p>
 */
public class CompactStager {

    /** Stage 1: thinking is front-capped at this many chars (the end — the conclusion — is kept). */
    static final int STAGE1_THINK_CAP = 9000;
    /** Stage 2/final: tool results, tool arguments and thinking are head-capped at this many chars. */
    static final int STAGE2_CAP = 6000;
    /** First line of the input-end disclosure (R-CIB-5). */
    static final String DISCLOSURE_MARKER = "session truncated";

    /** Per-message staging numbers for the diagnostic log (1-based index in the final input). */
    public record MessageStat(int index, String type, String toolName, int charsBefore, int charsAfter) {}

    public record Outcome(String input, int estimateBefore, int estimateAfter, CompactResult.Stage stage,
                          long droppedChars, int duplicatesCollapsed, List<MessageStat> messages) {}

    public Outcome stage(List<ChatMessage> messages, int budgetTokens) {
        // System messages never enter the compact input (static prompt, injected fresh on every
        // call) — filtered on list level, not by a silent render drop (ADR-0030 landmine fix).
        var candidates = messages.stream().filter(m -> !(m instanceof SystemMessage)).toList();

        // Dedup on the UNCAPPED renders, keep-first, O(n) (R-CIB-2): collapses LLM hangs
        // (50× the same file) without substring false-positives. Empty renders (CUSTOM) dropped.
        var entries = new ArrayList<Entry>();
        var seen = new LinkedHashSet<String>();
        int nonEmpty = 0;
        for (var m : candidates) {
            var render = ChatMessageUtil.toString(m, RenderOptions.uncapped());
            if (render.isEmpty()) continue;
            nonEmpty++;
            if (seen.add(render)) entries.add(new Entry(m, render));
        }
        int duplicatesCollapsed = nonEmpty - entries.size();

        int estimateBefore = estimate(uncapped(entries));

        // Budget off (≤ 0): never truncate, no disclosure (R-CIB-1).
        if (budgetTokens <= 0) {
            return outcome(entries, uncapped(entries), CompactResult.Stage.NONE, null, estimateBefore, duplicatesCollapsed);
        }
        // Under budget: nothing is truncated — except dedup, which must be disclosed, not silent (F4).
        if (estimateBefore <= budgetTokens) {
            var caps = duplicatesCollapsed > 0 ? capsLine(CompactResult.Stage.NONE, 0, duplicatesCollapsed) : null;
            return outcome(entries, uncapped(entries), CompactResult.Stage.NONE, caps, estimateBefore, duplicatesCollapsed);
        }

        var lastRealUser = lastRealUserMessage(entries);

        // Stage 1 (R-CIB-4.1): think capped to 9000 (front), only last real user message full,
        // tools rendered uncapped (capped in stage 2), earlier user messages state-only.
        var stage1 = capped(entries, e -> renderStage(e, RenderOptions.compactStage1(), e.message() == lastRealUser));
        if (estimate(stage1) <= budgetTokens) {
            return outcome(entries, stage1, CompactResult.Stage.THINK_AND_USER,
                    capsLine(CompactResult.Stage.THINK_AND_USER, 0, duplicatesCollapsed), estimateBefore, duplicatesCollapsed);
        }

        // Stage 2 (R-CIB-4.3): tool results + tool arguments + thinking capped at 6000.
        var stage2 = capped(entries, e -> renderStage(e, RenderOptions.compactStage2(), e.message() == lastRealUser));
        if (estimate(stage2) <= budgetTokens) {
            return outcome(entries, stage2, CompactResult.Stage.TOOL_RESULTS,
                    capsLine(CompactResult.Stage.TOOL_RESULTS, 0, duplicatesCollapsed), estimateBefore, duplicatesCollapsed);
        }

        // Final stage (R-CIB-4.4): per-message cap = restTokens×7/2/n, head-keep. Termination
        // guaranteed: sum ≤ n·capChars → estimate ≤ restTokens + overhead = budget.
        int n = Math.max(1, nonEmptyCount(stage2));
        int cap = perMessageCap(budgetTokens, n, duplicatesCollapsed, perMessageCap(budgetTokens, n, duplicatesCollapsed, 0));
        var finalCapped = stage2.stream().map(c -> new Capped(c.entry(), StringUtil.trimToLength(c.render(), cap))).toList();
        return outcome(entries, finalCapped, CompactResult.Stage.PER_MESSAGE,
                capsLine(CompactResult.Stage.PER_MESSAGE, cap, duplicatesCollapsed), estimateBefore, duplicatesCollapsed);
    }

    private record Entry(ChatMessage message, String uncapped) {}

    /** A deduped entry with its final render at the winning stage (empty = not part of the input). */
    private record Capped(Entry entry, String render) {}

    private static List<Capped> uncapped(List<Entry> entries) {
        return entries.stream().map(e -> new Capped(e, e.uncapped())).toList();
    }

    /** Re-renders the deduped entries in the given mode, keeping (entry, render) pairs so the
     *  diagnostic can name every message (state-only user messages may render empty). */
    private static List<Capped> capped(List<Entry> entries, Function<Entry, String> fn) {
        var result = new ArrayList<Capped>();
        for (var e : entries) result.add(new Capped(e, fn.apply(e)));
        return result;
    }

    private static int nonEmptyCount(List<Capped> capped) {
        return (int) capped.stream().filter(c -> !c.render().isEmpty()).count();
    }

    private static List<String> renders(List<Capped> capped) {
        return capped.stream().map(Capped::render).filter(r -> !r.isEmpty()).toList();
    }

    private static int estimate(List<Capped> capped) {
        return ChatMessageUtil.estimateTokens(String.join(System.lineSeparator(), renders(capped)));
    }

    /** State-only render of a user message: everything but the last TextContent (the real user
     *  text). Empty when the message carries nothing but (possibly excluded) real text. */
    private static String renderStateOnly(UserMessage um) {
        var texts = um.contents().stream().filter(c -> c instanceof TextContent).toList();
        if (texts.size() <= 1) return "";
        return ChatMessageUtil.toString(UserMessage.from(texts.subList(0, texts.size() - 1)), RenderOptions.uncapped());
    }

    private static String renderStage(Entry e, RenderOptions options, boolean lastRealUser) {
        var m = e.message();
        if (m instanceof UserMessage um) {
            // The last real user message stays full; earlier user messages are state-only
            // (everything but the last TextContent — context-message-concept.md).
            return lastRealUser ? e.uncapped() : renderStateOnly(um);
        }
        return ChatMessageUtil.toString(m, options);
    }

    /** The last UserMessage whose last TextContent is real user text (R-CIB-4.1); {@code null} if none. */
    private ChatMessage lastRealUserMessage(List<Entry> entries) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            var m = entries.get(i).message();
            if (m instanceof UserMessage um && hasRealUserText(um)) return m;
        }
        return null;
    }

    /**
     * Real user text = the LAST TextContent of a UserMessage (context-message-concept.md —
     * turn-context items first, the user text last). Compact hint, queued marker and reinsert
     * marker are state, referenced via their named constants (ADR-0056.4) — never literals.
     */
    private static boolean hasRealUserText(UserMessage um) {
        var texts = um.contents().stream().filter(c -> c instanceof TextContent).map(c -> (TextContent) c).toList();
        if (texts.isEmpty()) return false;
        var last = texts.get(texts.size() - 1).text();
        if (last == null) return false;
        return !last.startsWith(ToolService.COMPACT_HINT)
                && !last.startsWith(UserMessageQueue.QUEUED_MARKER_PREFIX)
                && !last.startsWith(CompactConstants.REINSERT_MARKER);
    }

    /**
     * Per-message cap from the tokens left after the disclosure + n separators (R-CIB-4.4, F5) —
     * tokens→chars with the ×2/7 estimator. The outer pass makes it a fixed point: the final
     * disclosure (digits of {@code cap} ≤ digits of the inner cap) is no longer than the one the
     * cap was computed against → the final estimate never exceeds the budget.
     */
    private int perMessageCap(int budgetTokens, int n, int duplicatesCollapsed, int capForDisclosure) {
        var nl = System.lineSeparator();
        var disclosure = DISCLOSURE_MARKER + nl + capsLine(CompactResult.Stage.PER_MESSAGE, capForDisclosure, duplicatesCollapsed);
        int restTokens = Math.max(0, budgetTokens - ChatMessageUtil.estimateTokens(disclosure + nl.repeat(n)));
        return (int) Math.min(Integer.MAX_VALUE, (long) restTokens * 7 / 2 / n);
    }

    /**
     * The single caps line of the disclosure (R-CIB-5): the applied caps in stage order, plus the
     * collapsed duplicates (never silent — F4). Only called when something was truncated.
     */
    private static String capsLine(CompactResult.Stage stage, int perMessageCap, int duplicatesCollapsed) {
        var parts = new ArrayList<String>();
        if (stage == CompactResult.Stage.THINK_AND_USER) {
            parts.add("thinking capped " + STAGE1_THINK_CAP + " (front)");
        } else if (stage != CompactResult.Stage.NONE) {
            parts.add("thinking capped " + STAGE2_CAP + " (front)");
            parts.add("tool results " + STAGE2_CAP);
            if (stage == CompactResult.Stage.PER_MESSAGE) parts.add("per-message cap " + perMessageCap);
        }
        if (duplicatesCollapsed > 0) parts.add("duplicates collapsed: " + duplicatesCollapsed);
        return String.join(", ", parts);
    }

    private Outcome outcome(List<Entry> entries, List<Capped> finalCapped, CompactResult.Stage stage, String caps,
                            int estimateBefore, int duplicatesCollapsed) {
        var nl = System.lineSeparator();
        var finalRenders = renders(finalCapped);
        String input = caps == null
                ? String.join(nl, finalRenders)
                : String.join(nl, finalRenders) + nl + DISCLOSURE_MARKER + nl + caps;
        long droppedChars = entries.stream().mapToLong(e -> (long) e.uncapped().length()).sum()
                - finalRenders.stream().mapToLong(String::length).sum();
        return new Outcome(input, estimateBefore, ChatMessageUtil.estimateTokens(input), stage, droppedChars,
                duplicatesCollapsed, messageStats(finalCapped));
    }

    /** 1-based index in the final input; entries that render empty (state-only) are not in the
     *  input and have no index — their chars still count into {@code droppedChars}. */
    private static List<MessageStat> messageStats(List<Capped> finalCapped) {
        var stats = new ArrayList<MessageStat>();
        int index = 0;
        for (var c : finalCapped) {
            if (c.render().isEmpty()) continue;
            index++;
            var m = c.entry().message();
            var toolName = m instanceof ToolExecutionResultMessage t ? t.toolName() : "";
            var type = switch (m.type()) {
                case TOOL_EXECUTION_RESULT -> "tool";
                default -> m.type().name().toLowerCase(Locale.ROOT);
            };
            stats.add(new MessageStat(index, type, toolName, c.entry().uncapped().length(), c.render().length()));
        }
        return List.copyOf(stats);
    }
}
