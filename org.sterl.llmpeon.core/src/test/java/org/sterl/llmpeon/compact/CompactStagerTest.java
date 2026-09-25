package org.sterl.llmpeon.compact;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.compact.CompactResult.Stage;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.tool.ToolService;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * Pure staging tests of the compact input (R-CIB-1…5, docs/compact.md §BDD) — no agent, no LLM.
 * Budgets are tokens (chars×2/7 estimator), fixture sizes are chosen so exactly one stage wins.
 */
class CompactStagerTest {

    private final CompactStager subject = new CompactStager();

    // ---------- budget off / under budget ----------

    @Test
    void zeroBudgetMeansNoCap() {
        // GIVEN — a history with a 20k-char thinking block and a 5k-char tool result
        var messages = List.<ChatMessage>of(ai("done", "T".repeat(20000)), toolResult("read", "y".repeat(5000)), user("q"));

        // WHEN — the budget is off (≤ 0)
        var outcome = subject.stage(messages, 0);

        // THEN — nothing is capped or dropped, no disclosure (R-CIB-1)
        assertThat(outcome.stage()).isEqualTo(Stage.NONE);
        assertThat(outcome.input()).contains("T".repeat(20000)).contains("y".repeat(5000));
        assertThat(outcome.input()).doesNotContain(ChatMessageUtil.THINK_FRONT_CAP_ANCHOR);
        assertThat(outcome.input()).doesNotContain("session truncated");
    }

    @Test
    void underBudget_nothingIsTruncated() {
        // GIVEN — 10k thinking + 8k tool result, plenty of budget
        var messages = List.<ChatMessage>of(ai("done", "T".repeat(10000)), toolResult("read", "y".repeat(8000)));

        // WHEN
        var outcome = subject.stage(messages, 100000);

        // THEN — every message is fully present, thinking unanchored
        assertThat(outcome.stage()).isEqualTo(Stage.NONE);
        assertThat(outcome.input()).contains("T".repeat(10000)).contains("y".repeat(8000));
        assertThat(outcome.input()).doesNotContain(ChatMessageUtil.THINK_FRONT_CAP_ANCHOR);
        assertThat(outcome.estimateAfter()).isLessThanOrEqualTo(100000);
    }

    @Test
    void underBudget_noDisclosure() {
        // GIVEN — nothing exceeds the budget
        var messages = List.<ChatMessage>of(ai("done", "T".repeat(10000)));

        // WHEN
        var outcome = subject.stage(messages, 100000);

        // THEN — no "session truncated" hint (R-CIB-5: nicht gekürzt → kein Hinweis)
        assertThat(outcome.stage()).isEqualTo(Stage.NONE);
        assertThat(outcome.input()).doesNotContain("session truncated");
    }

    // ---------- stage 1 ----------

    @Test
    void stage1_capsThinkingTo9000KeepingTheEnd() {
        // GIVEN — 30k-char thinking whose END carries the conclusion, budget only fits stage 1
        var thinking = "H".repeat(30000) + "CONCLUSION";
        var messages = List.<ChatMessage>of(ai("result", thinking));

        // WHEN
        var outcome = subject.stage(messages, 3000);

        // THEN — thinking is front-capped at 9000: the anchor marks the dropped head, the end stays
        assertThat(outcome.stage()).isEqualTo(Stage.THINK_AND_USER);
        assertThat(outcome.input()).contains("Think: " + ChatMessageUtil.THINK_FRONT_CAP_ANCHOR);
        assertThat(outcome.input()).contains("CONCLUSION");
        assertThat(outcome.input()).doesNotContain("H".repeat(20000));
        assertThat(outcome.estimateAfter()).isLessThanOrEqualTo(3000);
    }

    @Test
    void stage1_keepsOnlyLastRealUserMessageFull() {
        // GIVEN — an old and a current real user question, budget only fits stage 1
        var messages = List.<ChatMessage>of(
                user("old-question " + "A".repeat(2000)),
                ai("a1"),
                user("current-question " + "B".repeat(2000)),
                ai("a2", "T".repeat(15000)));

        // WHEN
        var outcome = subject.stage(messages, 4000);

        // THEN — the last real user message stays full, the earlier one is dropped (state-only, no state)
        assertThat(outcome.stage()).isEqualTo(Stage.THINK_AND_USER);
        assertThat(outcome.input()).contains("B".repeat(2000));
        assertThat(outcome.input()).doesNotContain("old-question");
    }

    @Test
    void stage1_earlierUserMessagesRenderStateOnly() {
        // GIVEN — an earlier user message with a turn-context item FIRST and the real text LAST,
        // plus a later real user message (so the earlier one is NOT the last real user)
        var earlier = UserMessage.from(List.of(
                TextContent.from("FILE: /src/Foo.java"),
                TextContent.from("please fix the bug")));
        var messages = List.<ChatMessage>of(earlier, ai("ok", "T".repeat(15000)), user("current question"));

        // WHEN
        var outcome = subject.stage(messages, 3000);

        // THEN — only the state (context item) of the earlier message survives, its real text is dropped
        assertThat(outcome.stage()).isEqualTo(Stage.THINK_AND_USER);
        assertThat(outcome.input()).contains("FILE: /src/Foo.java");
        assertThat(outcome.input()).doesNotContain("please fix the bug");
    }

    @Test
    void stage1_hintIsNeverTreatedAsRealUserText() {
        // GIVEN — the LAST user message is the COMPACT_HINT, an earlier one is real user text
        var messages = List.<ChatMessage>of(
                user("real question " + "D".repeat(1000)),
                ai("ok", "T".repeat(15000)),
                user(ToolService.COMPACT_HINT + " (now)"));

        // WHEN
        var outcome = subject.stage(messages, 3000);

        // THEN — the hint is state (constant exclusion), the earlier real message is the full one
        assertThat(outcome.stage()).isEqualTo(Stage.THINK_AND_USER);
        assertThat(outcome.input()).contains("D".repeat(1000));
        assertThat(outcome.input()).doesNotContain("CONTEXT LIMIT WARNING");
    }

    @Test
    void stage1_noUserMessageWithRealText_noUserReduction() {
        // GIVEN — only AI + tool messages, no user message at all
        var messages = List.<ChatMessage>of(ai("a1", "T".repeat(15000)), toolResult("read", "y".repeat(3000)));

        // WHEN
        var outcome = subject.stage(messages, 4000);

        // THEN — stage 1 runs without a user reduction target; AI text and (uncapped) tools stay full
        assertThat(outcome.stage()).isEqualTo(Stage.THINK_AND_USER);
        assertThat(outcome.input()).contains("a1").contains("y".repeat(3000));
    }

    // ---------- stage 2 ----------

    @Test
    void stage2_capsToolResultsToolArgumentsAndThinkingTo6000() {
        // GIVEN — 12k thinking + 12k tool arguments + 12k tool result; stage 1 (tools uncapped)
        // is still over budget, stage 2 (all three at 6000) fits
        var messages = List.<ChatMessage>of(
                AiMessage.builder()
                        .text("a")
                        .thinking("T".repeat(12000))
                        .toolExecutionRequests(List.of(
                                ToolExecutionRequest.builder().id("1").name("write").arguments("A".repeat(12000)).build()))
                        .build(),
                toolResult("write", "R".repeat(12000)));

        // WHEN
        var outcome = subject.stage(messages, 7000);

        // THEN — thinking, tool arguments and tool results are all capped at 6000 (R-CIB-4.3, F3)
        assertThat(outcome.stage()).isEqualTo(Stage.TOOL_RESULTS);
        assertThat(outcome.input()).contains("T".repeat(5000)).doesNotContain("T".repeat(12000));
        assertThat(outcome.input()).contains("A".repeat(6000)).doesNotContain("A".repeat(6001));
        assertThat(outcome.input()).contains("R".repeat(6000)).doesNotContain("R".repeat(6001));
        assertThat(outcome.estimateAfter()).isLessThanOrEqualTo(7000);
    }

    // ---------- final stage ----------

    @Test
    void finalStage_capsEachMessageToRestTokensTimes7Over2OverN() {
        // GIVEN — three messages, none of the earlier stages fits a 1000-token budget
        var messages = List.<ChatMessage>of(ai("a", "T".repeat(12000)), toolResult("write", "R".repeat(12000)),
                user("q " + "U".repeat(3000)));

        // WHEN
        var outcome = subject.stage(messages, 1000);

        // THEN — the per-message cap (restTokens×7/2/n) is applied to every finished render:
        // no 2000-char run of any filler survives (stage 2 alone would leave 6000-char runs),
        // but the content heads are still there
        assertThat(outcome.stage()).isEqualTo(Stage.PER_MESSAGE);
        assertThat(outcome.input()).doesNotContain("T".repeat(2000)).doesNotContain("R".repeat(2000)).doesNotContain("U".repeat(2000));
        assertThat(outcome.input()).contains("T".repeat(1000)).contains("TOOL_EXECUTION_RESULT:").contains("USER:\nq U");
    }

    @Test
    void finalStage_estimateStaysWithinBudget_guaranteed() {
        // GIVEN — fixtures and budgets where only the final stage can fit
        var multi = List.<ChatMessage>of(ai("a", "T".repeat(12000)), toolResult("write", "R".repeat(12000)),
                user("q " + "U".repeat(3000)));
        var single = List.<ChatMessage>of(toolResult("big", "Z".repeat(50000)));

        // WHEN/THEN — for every budget the final estimate never exceeds it (R-CIB-4.4 termination
        // guarantee; mutation ×7/2→×7 or ×2/7 must turn this red)
        for (var budget : List.of(100, 500, 1000, 4000)) {
            var outcome = subject.stage(multi, budget);
            assertThat(outcome.stage()).as("multi, budget %d".formatted(budget)).isEqualTo(Stage.PER_MESSAGE);
            assertThat(outcome.estimateAfter()).as("multi, budget %d".formatted(budget))
                    .isLessThanOrEqualTo(budget);
        }
        for (var budget : List.of(100, 500, 1000)) {
            var outcome = subject.stage(single, budget);
            assertThat(outcome.stage()).as("single, budget %d".formatted(budget)).isEqualTo(Stage.PER_MESSAGE);
            assertThat(outcome.estimateAfter()).as("single, budget %d".formatted(budget))
                    .isLessThanOrEqualTo(budget);
        }
    }

    @Test
    void finalStage_singleMessageLargerThanBudget_stillCompacts() {
        // GIVEN — one 50k-char tool result, budget 500 tokens
        var messages = List.<ChatMessage>of(toolResult("big", "Z".repeat(50000)));

        // WHEN
        var outcome = subject.stage(messages, 500);

        // THEN — the message is capped, the compact still delivers
        assertThat(outcome.stage()).isEqualTo(Stage.PER_MESSAGE);
        assertThat(outcome.input()).isNotBlank();
        assertThat(outcome.droppedChars()).isPositive();
        assertThat(outcome.estimateAfter()).isLessThanOrEqualTo(500);
    }

    // ---------- dedup ----------

    @Test
    void dedup_keepsFirstOfIdenticalMessages() {
        // GIVEN — the same tool result twice (LLM hang), under budget
        var messages = List.<ChatMessage>of(toolResult("read", "SAME-CONTENT"), toolResult("read", "SAME-CONTENT"), user("q"));

        // WHEN
        var outcome = subject.stage(messages, 100000);

        // THEN — keep-first on the uncapped renders, the duplicate is counted
        assertThat(outcome.stage()).isEqualTo(Stage.NONE);
        assertThat(countOccurrences(outcome.input(), "SAME-CONTENT")).isEqualTo(1);
        assertThat(outcome.duplicatesCollapsed()).isEqualTo(1);
        assertThat(outcome.input()).contains("duplicates collapsed: 1");
    }

    @Test
    void dedup_doesNotCollapseDifferentMessagesWithEqualHead() {
        // GIVEN — two different tool results sharing a 5-char head (substring dedup would collapse them)
        var messages = List.<ChatMessage>of(toolResult("read", "HEAD-" + "A".repeat(100)),
                toolResult("read", "HEAD-" + "B".repeat(100)));

        // WHEN
        var outcome = subject.stage(messages, 100000);

        // THEN — full-render dedup keeps both (R-CIB-2)
        assertThat(outcome.duplicatesCollapsed()).isZero();
        assertThat(outcome.input()).contains("A".repeat(100)).contains("B".repeat(100));
        assertThat(outcome.input()).doesNotContain("session truncated");
    }

    @Test
    void dedupOnly_disclosesDuplicatesCollapsed() {
        // GIVEN — five identical tool results, under budget (dedup alone brings it down)
        var messages = new ArrayList<ChatMessage>();
        for (var i = 0; i < 5; i++) messages.add(toolResult("read", "SAME"));
        messages.add(user("q"));

        // WHEN
        var outcome = subject.stage(messages, 100000);

        // THEN — the collapse is disclosed, not silent (F4)
        assertThat(outcome.stage()).isEqualTo(Stage.NONE);
        assertThat(outcome.estimateAfter()).isLessThanOrEqualTo(100000);
        assertThat(outcome.input()).contains("session truncated").contains("duplicates collapsed: 4");
    }

    // ---------- disclosure ----------

    @Test
    void disclosure_appendedOnceAtEnd_whenCapped() {
        // GIVEN — stage 1 kicks in (15k thinking, budget 3000)
        var messages = List.<ChatMessage>of(ai("a", "T".repeat(15000)));

        // WHEN
        var outcome = subject.stage(messages, 3000);

        // THEN — "session truncated" + one caps line, exactly once, at the input end (R-CIB-5);
        // no per-message "(trimmed)" tags in compact mode
        var nl = System.lineSeparator();
        assertThat(countOccurrences(outcome.input(), "session truncated")).isEqualTo(1);
        assertThat(countOccurrences(outcome.input(), ChatMessageUtil.THINK_FRONT_CAP_ANCHOR)).isEqualTo(1);
        assertThat(outcome.input()).endsWith("session truncated" + nl + "thinking capped 9000 (front)");
        assertThat(outcome.input()).doesNotContain("(trimmed)");
    }

    // ---------- reinsert markers (R-CC-9) ----------

    @Test
    void reinsertedSummaryAndMarkerAreStateNeverRealUserText() {
        // GIVEN — a reinserted "Session compacted:" user message, then a later real question
        var messages = List.<ChatMessage>of(
                user(CompactConstants.REINSERT_MARKER + " summary text here"),
                ai("summary"),
                user("later question " + "E".repeat(2000)),
                ai("a", "T".repeat(15000)));

        // WHEN
        var outcome = subject.stage(messages, 3500);

        // THEN — the reinsert marker message is state (constant exclusion), the later question is the
        // full last real user message
        assertThat(outcome.stage()).isEqualTo(Stage.THINK_AND_USER);
        assertThat(outcome.input()).contains("E".repeat(2000));
        assertThat(outcome.input()).doesNotContain("summary text here");
    }

    // ---------- helpers ----------

    /** System messages never enter the staging (filtered on list level, ADR-0030 landmine fix). */
    @Test
    void systemMessagesAreFilteredOnListLevel() {
        // GIVEN — a system message plus a user message
        var messages = List.<ChatMessage>of(SystemMessage.systemMessage("STATIC PROMPT"), user("q"));

        // WHEN
        var outcome = subject.stage(messages, 100000);

        // THEN — the system message is absent from the input
        assertThat(outcome.input()).doesNotContain("STATIC PROMPT");
        assertThat(outcome.input()).contains("q");
    }

    private static UserMessage user(String text) {
        return UserMessage.from(text);
    }

    private static AiMessage ai(String text, String thinking) {
        return AiMessage.builder().text(text).thinking(thinking).build();
    }

    private static AiMessage ai(String text) {
        return AiMessage.from(text);
    }

    private static ToolExecutionResultMessage toolResult(String toolName, String text) {
        return ToolExecutionResultMessage.from("id", toolName, text);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (var i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) count++;
        return count;
    }
}
