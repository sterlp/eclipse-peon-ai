package org.sterl.llmpeon.compact;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.agent.model.TaggedResult;
import org.sterl.llmpeon.model.CompactResult;
import org.sterl.llmpeon.queuedmessages.UserMessageQueue;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.ToolService;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * Pure compact input staging (R-CIB-4): (memory snapshot, budget) → (input, disclosure, stats).
 * No agent/memory/config types — a message list plus an int, testable without mocks.
 *
 * <p>Pipeline: extract the per-message blocks (ONE render implementation, uncapped — R-TR-8) →
 * drop pure context blocks (counted — R-TR-9) and SYSTEM blocks (silent — ADR-0030) → join →
 * dedup on the joined renders (LinkedHashSet, keep-first, before any caps — R-CIB-2) → estimate
 * (chars/3, R-CIB-1) → stage 1 (thinking 9000 front-cap, only the last real user message full)
 * → re-estimate → stage 2 (tool results/arguments + thinking 6000) → re-estimate → final stage
 * (per-message cap = restTokens×3/n, head-keep, termination guaranteed). The stage caps classify
 * on the tag prefixes only (R-TR-7) — no instanceof. The disclosure ("session truncated" + one
 * caps line) is appended once at the input end when something was capped or collapsed; the
 * "context blocks dropped: K" line rides along as its own line, budget-independent, only when
 * K &gt; 0 (R-TR-9: the drop is a filter, not a cap — named, never silent).</p>
 */
public class ContextTrimComponent {

    /** Stage 1: thinking is front-capped at this many chars (the end — the conclusion — is kept). */
    static final int STAGE1_THINK_CAP = 9000;
    /** Stage 2/final: tool results, tool arguments and thinking are head-capped at this many chars. */
    static final int STAGE2_CAP = 6000;
    /** First line of the input-end disclosure (R-CIB-5). */
    static final String DISCLOSURE_MARKER = "session truncated";
    /** R-TR-9 disclosure line (own line, budget-independent, only when K > 0). */
    static final String CONTEXT_BLOCKS_DROPPED = "context blocks dropped: ";

    /** Per-message staging numbers for the diagnostic log (1-based index in the final input). */
    public record MessageStat(int index, String type, String toolName, int charsBefore, int charsAfter) {}

    public record Outcome(String input, int estimateBefore, int estimateAfter, CompactResult.Stage stage,
                          long droppedChars, int duplicatesCollapsed, int contextBlocksDropped,
                          List<MessageStat> messages) {}

    public Outcome stage(List<ChatMessage> messages, int budgetTokens) {
        // ONE render implementation (R-TR-8): the per-message blocks, uncapped. Pure context
        // blocks fly out BEFORE the compact input (R-TR-9, counted — a filter, not a cap);
        // SYSTEM blocks are dropped silent (ADR-0030: the static prompt never enters the input).
        var entries = new ArrayList<Entry>();
        var seen = new LinkedHashSet<String>();
        int nonEmpty = 0;
        int contextBlocksDropped = 0;
        for (var m : messages) {
            var drop = dropBlocks(ChatMessageUtil.extractMessages(m));
            contextBlocksDropped += drop.dropped();
            var render = String.join(System.lineSeparator(), drop.kept());
            if (render.isEmpty()) continue;
            nonEmpty++;
            // Dedup on the joined renders, keep-first, O(n) (R-CIB-2): collapses LLM hangs
            // (50× the same file) without substring false-positives.
            if (seen.add(render)) entries.add(new Entry(m, drop.kept(), render));
        }
        int duplicatesCollapsed = nonEmpty - entries.size();
        int estimateBefore = estimate(uncapped(entries));

        // Budget off (≤ 0): never truncate, no cap disclosure (R-CIB-1) — the R-TR-9 drop line
        // is a filter disclosure and stays, budget-independent.
        if (budgetTokens <= 0) {
            return outcome(entries, uncapped(entries), CompactResult.Stage.NONE, duplicatesCollapsed,
                    contextBlocksDropped, estimateBefore, null);
        }
        // Under budget: nothing is truncated — except dedup, which must be disclosed, not silent (F4).
        if (estimateBefore <= budgetTokens) {
            var caps = duplicatesCollapsed > 0 ? capsLine(CompactResult.Stage.NONE, 0, duplicatesCollapsed) : null;
            return outcome(entries, uncapped(entries), CompactResult.Stage.NONE, duplicatesCollapsed,
                    contextBlocksDropped, estimateBefore, caps);
        }

        var lastRealUser = lastRealUserMessage(entries);

        // Stage 1 (R-CIB-4.1): think front-capped at 9000, only the last real user message full,
        // tools uncapped (capped in stage 2), earlier user messages state-only.
        var stage1 = staged(entries, STAGE1_THINK_CAP, 0, lastRealUser);
        if (estimate(stage1) <= budgetTokens) {
            return outcome(entries, stage1, CompactResult.Stage.THINK_AND_USER, duplicatesCollapsed,
                    contextBlocksDropped, estimateBefore,
                    capsLine(CompactResult.Stage.THINK_AND_USER, 0, duplicatesCollapsed));
        }

        // Stage 2 (R-CIB-4.3): tool results + tool arguments + thinking capped at 6000.
        var stage2 = staged(entries, STAGE2_CAP, STAGE2_CAP, lastRealUser);
        if (estimate(stage2) <= budgetTokens) {
            return outcome(entries, stage2, CompactResult.Stage.TOOL_RESULTS, duplicatesCollapsed,
                    contextBlocksDropped, estimateBefore,
                    capsLine(CompactResult.Stage.TOOL_RESULTS, 0, duplicatesCollapsed));
        }

        // Final stage (R-CIB-4.4): per-message cap = restTokens×3/n, head-keep. Termination
        // guaranteed: sum ≤ n·capChars → estimate ≤ restTokens + overhead = budget.
        int n = Math.max(1, nonEmptyCount(stage2));
        int cap = perMessageCap(budgetTokens, n, duplicatesCollapsed, contextBlocksDropped,
                perMessageCap(budgetTokens, n, duplicatesCollapsed, contextBlocksDropped, 0));
        var finalCapped = stage2.stream().map(c -> new Capped(c.entry(), StringUtil.trimToLength(c.render(), cap))).toList();
        return outcome(entries, finalCapped, CompactResult.Stage.PER_MESSAGE, duplicatesCollapsed,
                contextBlocksDropped, estimateBefore,
                capsLine(CompactResult.Stage.PER_MESSAGE, cap, duplicatesCollapsed));
    }

    /** A deduped entry: the message, its surviving blocks (pure context + SYSTEM dropped) and the
     *  joined uncapped render (the dedup key). */
    private record Entry(ChatMessage message, List<String> blocks, String uncapped) {}

    /** A deduped entry with its final render at the winning stage (empty = not part of the input). */
    private record Capped(Entry entry, String render) {}

    /** The R-TR-9/ADR-0030 drop: pure context blocks are counted (K), SYSTEM blocks stay silent. */
    private record BlockDrop(List<String> kept, int dropped) {}

    private static BlockDrop dropBlocks(Collection<String> blocks) {
        var kept = new ArrayList<String>();
        int dropped = 0;
        for (var b : blocks) {
            if (b.startsWith(ChatMessageUtil.SYSTEM_PREFIX)) continue;
            if (TaggedResult.isTagged(b)) { dropped++; continue; }
            kept.add(b);
        }
        return new BlockDrop(List.copyOf(kept), dropped);
    }

    private static List<Capped> uncapped(List<Entry> entries) {
        return entries.stream().map(e -> new Capped(e, e.uncapped())).toList();
    }

    /** Re-renders the deduped entries at the given stage caps — classification on the tag
     *  prefixes only (R-TR-7, no instanceof for capping) — keeping (entry, render) pairs so the
     *  diagnostic can name every message (state-only user messages may render empty). */
    private static List<Capped> staged(List<Entry> entries, int thinkCap, int toolCap, ChatMessage lastRealUser) {
        var result = new ArrayList<Capped>();
        for (var e : entries) result.add(new Capped(e, renderBlocks(e, thinkCap, toolCap, lastRealUser)));
        return result;
    }

    private static String renderBlocks(Entry e, int thinkCap, int toolCap, ChatMessage lastRealUser) {
        var m = e.message();
        if (m instanceof UserMessage um) {
            // The last real user message stays full; earlier user messages are state-only
            // (everything but the last TextContent — context-message-concept.md).
            return m == lastRealUser ? e.uncapped() : renderStateOnly(um);
        }
        var blocks = new ArrayList<String>(e.blocks().size());
        for (var b : e.blocks()) {
            if (b.startsWith(ChatMessageUtil.THINK_PREFIX)) {
                blocks.add(frontCapThinking(b, thinkCap));
            } else if (toolCap > 0 && b.startsWith(ChatMessageUtil.TOOL_RESULT_PREFIX)) {
                blocks.add(headCapBody(b, ChatMessageUtil.TOOL_RESULT_BODY_MARKER, toolCap, true));
            } else if (toolCap > 0 && b.startsWith(ChatMessageUtil.TOOL_REQUEST_PREFIX)) {
                blocks.add(headCapBody(b, ChatMessageUtil.TOOL_REQUEST_BODY_MARKER, toolCap, false));
            } else {
                blocks.add(b);
            }
        }
        return String.join(System.lineSeparator(), blocks);
    }

    /** Front cap: keep the END of the thinking (the conclusion), drop the head, mark with the anchor. */
    private static String frontCapThinking(String block, int cap) {
        int nl = block.indexOf('\n');
        if (nl < 0) return block;
        var body = block.substring(nl + 1);
        if (body.length() <= cap) return block;
        return ChatMessageUtil.THINK_PREFIX + ChatMessageUtil.THINK_FRONT_CAP_ANCHOR + body.substring(body.length() - cap);
    }

    /** Head-keep cap of the body after the marker: keep the first {@code cap} chars, drop the tail.
     *  The TOOL RESULT: body is measured from the LAST marker occurrence, the TOOL REQUEST:
     *  arguments from the structural (first) one. */
    private static String headCapBody(String block, String marker, int cap, boolean lastOccurrence) {
        int idx = lastOccurrence ? block.lastIndexOf(marker) : block.indexOf(marker);
        if (idx < 0) return block;
        int bodyStart = idx + marker.length();
        var body = block.substring(bodyStart);
        if (body.length() <= cap) return block;
        return block.substring(0, bodyStart) + body.substring(0, cap);
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
     *  text) — the entries run through the SAME drop pipeline (pure blocks fly out, R-TR-9; the
     *  count was already taken on the full message). Empty when nothing but real text is left. */
    private static String renderStateOnly(UserMessage um) {
        var texts = um.contents().stream().filter(c -> c instanceof TextContent).toList();
        if (texts.size() <= 1) return "";
        return String.join(System.lineSeparator(),
                dropBlocks(ChatMessageUtil.extractMessages(UserMessage.from(texts.subList(0, texts.size() - 1)))).kept());
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
     * tokens→chars via {@link ChatMessageUtil#tokensToChars(int)}. The R-TR-9 drop line is part
     * of the disclosure it sizes against. The outer pass makes it a fixed point: the final
     * disclosure (digits of {@code cap} ≤ digits of the inner cap) is no longer than the one the
     * cap was computed against → the final estimate never exceeds the budget.
     */
    private int perMessageCap(int budgetTokens, int n, int duplicatesCollapsed, int contextBlocksDropped, int capForDisclosure) {
        var nl = System.lineSeparator();
        var kLine = contextBlocksDropped > 0 ? nl + CONTEXT_BLOCKS_DROPPED + contextBlocksDropped : "";
        var disclosure = DISCLOSURE_MARKER + nl + capsLine(CompactResult.Stage.PER_MESSAGE, capForDisclosure, duplicatesCollapsed) + kLine;
        int restTokens = Math.max(0, budgetTokens - ChatMessageUtil.estimateTokens(disclosure + nl.repeat(n)));
        return ChatMessageUtil.tokensToChars(restTokens) / n;
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

    private Outcome outcome(List<Entry> entries, List<Capped> finalCapped, CompactResult.Stage stage,
                            int duplicatesCollapsed, int contextBlocksDropped, int estimateBefore, @Nullable String caps) {
        var nl = System.lineSeparator();
        var finalRenders = renders(finalCapped);
        // The R-TR-9 drop line is its own line, budget-independent, only when K > 0 (a filter,
        // not a cap — named, never silent).
        var disclosure = new ArrayList<String>();
        if (caps != null) disclosure.add(caps);
        if (contextBlocksDropped > 0) disclosure.add(CONTEXT_BLOCKS_DROPPED + contextBlocksDropped);
        String input = disclosure.isEmpty()
                ? String.join(nl, finalRenders)
                : String.join(nl, finalRenders) + nl + DISCLOSURE_MARKER + nl + String.join(nl, disclosure);
        long droppedChars = entries.stream().mapToLong(e -> (long) e.uncapped().length()).sum()
                - finalRenders.stream().mapToLong(String::length).sum();
        return new Outcome(input, estimateBefore, ChatMessageUtil.estimateTokens(input), stage, droppedChars,
                duplicatesCollapsed, contextBlocksDropped, messageStats(finalCapped));
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
