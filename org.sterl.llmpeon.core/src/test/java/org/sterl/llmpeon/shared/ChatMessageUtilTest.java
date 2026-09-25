package org.sterl.llmpeon.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.List;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

class ChatMessageUtilTest {

    // R-CIB-4 / ADR-0030-Landmine — a SYSTEM message must be rendered, not silently dropped.

    @Test
    void systemMessageIsRenderedNotDropped() {
        // GIVEN a SYSTEM message
        var msg = SystemMessage.from("SYS-RULES");
        // WHEN rendered with the default options
        String out = ChatMessageUtil.toString(msg);
        // THEN it is rendered like every other type (ADR-0030 landmine fix), not dropped
        assertThat(out).isEqualTo("SYSTEM:" + System.lineSeparator() + "SYS-RULES" + System.lineSeparator());
    }

    // Render-Modi (ADR-0056.2) — Bestandsformate bleiben per Default exakt erhalten; Compact-Modi schalten nur Extras.

    @Test
    void defaultOverloadsUnchanged() {
        // GIVEN — one message per legacy signature
        var userMsg = UserMessage.from("USER-TEXT");
        var aiMsg = AiMessage.builder().text("AI-TEXT").thinking("AI-THINK").build();
        var toolMsg = ToolExecutionResultMessage.from("id-1", "echo", "RESULT-12345");
        var nl = System.lineSeparator();

        // WHEN — the 1-arg default (includeThink=true, tool cap 6000)
        // THEN — the user format is unchanged
        assertThat(ChatMessageUtil.toString(userMsg))
            .isEqualTo("USER:" + nl + "USER-TEXT" + nl);
        // THEN — the AI format carries the thinking uncapped, no anchor
        assertThat(ChatMessageUtil.toString(aiMsg))
            .isEqualTo("AI:" + nl + "AI-TEXT" + nl + "Think: AI-THINK" + nl);
        // THEN — a tool result below the 6000 default cap passes through untagged
        assertThat(ChatMessageUtil.toString(toolMsg))
            .isEqualTo("TOOL_EXECUTION_RESULT:" + nl + "tool name: echo" + nl + "result:" + nl + "RESULT-12345" + nl);

        // WHEN — the 2-arg signature applies its int cap (head-keep + tag)
        // THEN — the format matches the legacy behaviour
        assertThat(ChatMessageUtil.toString(toolMsg, 3))
            .isEqualTo("TOOL_EXECUTION_RESULT:" + nl + "tool name: echo" + nl + "result:" + nl + "RES" + nl + "(trimmed)" + nl);

        // WHEN — the 3-arg signature switches off thinking
        // THEN — the Think line is absent and the int cap hits the tool arguments
        var toolReq = ToolExecutionRequest.builder().id("id-2").name("echo").arguments("ABCDEFGH").build();
        var aiToolMsg = AiMessage.from("ANSWER", List.of(toolReq));
        assertThat(ChatMessageUtil.toString(aiToolMsg, false, 5))
            .isEqualTo("AI:" + nl + "ANSWER" + nl + "tool name: echo" + nl + "arguments:" + nl + "ABCDE" + nl + "(trimmed)" + nl);
    }

    @Test
    void thinkFrontCapKeepsTailWithAnchor() {
        // GIVEN — a thinking longer than the compact stage-1 cap (9000)
        var thinking = "P".repeat(8000) + "END-" + "Q".repeat(2000); // 10004 chars
        var msg = AiMessage.builder().text("ANSWER").thinking(thinking).build();
        var nl = System.lineSeparator();

        // WHEN rendered in compact stage 1
        String out = ChatMessageUtil.toString(msg, ChatMessageUtil.RenderOptions.compactStage1());

        // THEN the head is dropped, the end (conclusion) is kept, the anchor marks the cut
        assertThat(out).isEqualTo("AI:" + nl + "ANSWER" + nl + "Think: " + ChatMessageUtil.THINK_FRONT_CAP_ANCHOR
            + thinking.substring(thinking.length() - 9000) + nl);
        // AND no tool block is rendered at all in stage 1
        assertThat(out).doesNotContain("tool name:");
    }

    @Test
    void thinkFullByDefault() {
        // GIVEN — a thinking far below any cap
        var msg = AiMessage.builder().text("A").thinking("T-THINK").build();

        // WHEN rendered with the default options
        String out = ChatMessageUtil.toString(msg);

        // THEN the full thinking is kept, no anchor
        assertThat(out).isEqualTo("AI:" + System.lineSeparator() + "A" + System.lineSeparator() + "Think: T-THINK" + System.lineSeparator())
            .doesNotContain(ChatMessageUtil.THINK_FRONT_CAP_ANCHOR);
    }

    @Test
    void trimmedTagSuppressedInCompactMode() {
        // GIVEN — a tool result beyond the compact stage-2 cap (6000)
        var msg = ToolExecutionResultMessage.from("id-3", "big", "X".repeat(7000));
        var nl = System.lineSeparator();

        // WHEN rendered in compact stage 2
        String out = ChatMessageUtil.toString(msg, ChatMessageUtil.RenderOptions.compactStage2());

        // THEN capped, but no per-message "(trimmed)" tag (R-CIB-5: one disclosure at the input end)
        assertThat(out).isEqualTo("TOOL_EXECUTION_RESULT:" + nl + "tool name: big" + nl + "result:" + nl + "X".repeat(6000) + nl)
            .doesNotContain("(trimmed)");
        // AND the legacy default still tags
        assertThat(ChatMessageUtil.toString(msg)).contains("(trimmed)");
    }

    @Test
    void toolArgumentsCappedByToolMessageSize() {
        // GIVEN — a tool request whose arguments exceed the compact stage-2 cap (6000)
        var req = ToolExecutionRequest.builder().id("id-4").name("write").arguments("A".repeat(7000)).build();
        var msg = AiMessage.from("WRITING", List.of(req));
        var nl = System.lineSeparator();

        // WHEN rendered in compact stage 2
        String out = ChatMessageUtil.toString(msg, ChatMessageUtil.RenderOptions.compactStage2());

        // THEN the arguments are head-capped at 6000, no per-message tag
        assertThat(out).isEqualTo("AI:" + nl + "WRITING" + nl + "tool name: write" + nl + "arguments:" + nl + "A".repeat(6000) + nl);
    }



    // R21 — the estimator the live status uses for per-chunk token counting.

    @Test
    void estimates_32_chars_to_9() {
        // GIVEN a 32-char snippet (R21 BDD)
        // WHEN
        int tokens = ChatMessageUtil.estimateTokens("a".repeat(32));
        // THEN (32 * 2) / 7 = 9
        assertThat(tokens).isEqualTo(9);
    }

    @Test
    void estimates_26_chars_to_7() {
        // GIVEN a 26-char snippet (R21 BDD)
        // WHEN
        int tokens = ChatMessageUtil.estimateTokens("b".repeat(26));
        // THEN (26 * 2) / 7 = 7
        assertThat(tokens).isEqualTo(7);
    }

    @Test
    void estimates_short_snippet_to_one() {
        // GIVEN a snippet of 5 chars or less (R21 BDD: "Hi" → 1)
        // WHEN
        int tokens = ChatMessageUtil.estimateTokens("Hi");
        // THEN any non-empty snippet up to 5 chars counts as one token
        assertThat(tokens).isEqualTo(1);
    }

    @Test
    void boundary_five_chars_is_one() {
        // GIVEN a snippet of exactly 5 chars
        // WHEN
        int tokens = ChatMessageUtil.estimateTokens("12345");
        // THEN still the short-snippet floor
        assertThat(tokens).isEqualTo(1);
    }

    @Test
    void boundary_six_chars_is_one() {
        // GIVEN a snippet of exactly 6 chars — first length past the short floor
        // WHEN
        int tokens = ChatMessageUtil.estimateTokens("123456");
        // THEN (6 * 2) / 7 = 1
        assertThat(tokens).isEqualTo(1);
    }

    @Test
    void empty_string_counts_zero() {
        // GIVEN an empty snippet (no content to count)
        // WHEN
        int tokens = ChatMessageUtil.estimateTokens("");
        // THEN
        assertThat(tokens).isZero();
    }

    @Test
    void null_counts_zero() {
        // GIVEN no snippet at all
        // WHEN
        int tokens = ChatMessageUtil.estimateTokens((String) null);
        // THEN a missing snippet never contributes tokens
        assertThat(tokens).isZero();
    }
}
