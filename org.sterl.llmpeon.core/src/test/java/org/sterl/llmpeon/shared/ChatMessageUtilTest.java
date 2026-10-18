package org.sterl.llmpeon.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.agent.model.TaggedResult;

import java.util.List;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
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

    // Single render (R-TR-8) — UNCAPPED, byte-exact formats; capping is the stager's job (ContextTrimComponentTest).

    @Test
    void singleOverloadRendersUncapped() {
        // GIVEN — one message per type
        var userMsg = UserMessage.from("USER-TEXT");
        var aiMsg = AiMessage.builder().text("AI-TEXT").thinking("AI-THINK").build();
        var toolMsg = ToolExecutionResultMessage.from("id-1", "echo", "RESULT-12345");
        var toolReq = ToolExecutionRequest.builder().id("id-2").name("echo").arguments("ABCDEFGH").build();
        var aiToolMsg = AiMessage.from("ANSWER", List.of(toolReq));
        var nl = System.lineSeparator();

        // WHEN — the single 1-arg render (R-TR-8: uncapped — capping is the stager's job)
        // THEN — the user format is unchanged
        assertThat(ChatMessageUtil.toString(userMsg))
            .isEqualTo("USER:" + nl + "USER-TEXT" + nl);
        // THEN — the AI format carries the thinking as its own THINK: block (D1), uncapped, no anchor
        assertThat(ChatMessageUtil.toString(aiMsg))
            .isEqualTo("AI:" + nl + "AI-TEXT" + nl + "THINK:" + nl + "AI-THINK" + nl);
        // THEN — a tool result is rendered in full (D3: TOOL RESULT: header, no cap, no "(trimmed)")
        assertThat(ChatMessageUtil.toString(toolMsg))
            .isEqualTo("TOOL RESULT:" + nl + "tool name: echo" + nl + "result:" + nl + "RESULT-12345" + nl);
        // THEN — a tool request renders its arguments in full (D2: TOOL REQUEST: block, arguments: without NL)
        assertThat(ChatMessageUtil.toString(aiToolMsg))
            .isEqualTo("AI:" + nl + "ANSWER" + nl + "TOOL REQUEST:" + nl + "tool name: echo" + nl + "arguments:ABCDEFGH" + nl);
    }

    @Test
    void thinkFullByDefault() {
        // GIVEN — a thinking far below any cap
        var msg = AiMessage.builder().text("A").thinking("T-THINK").build();

        // WHEN rendered with the default options
        String out = ChatMessageUtil.toString(msg);

        // THEN the full thinking is kept in its own THINK: block (D1), no anchor
        assertThat(out).isEqualTo("AI:" + System.lineSeparator() + "A" + System.lineSeparator() + "THINK:" + System.lineSeparator() + "T-THINK" + System.lineSeparator())
            .doesNotContain(ChatMessageUtil.THINK_FRONT_CAP_ANCHOR);
    }

    // R-TR-1/2 — the tagged block: symmetric render, line-wise detection, header suppression.

    // R-TR-1
    @Test
    void taggedResultRendersSymmetricBlock() {
        // WHEN a tagged result is rendered
        String rendered = new TaggedResult("h", "b").toString();

        // THEN — the end line carries the head (symmetric block, no trailing line separator)
        assertThat(rendered).isEqualTo(TaggedResult.PREFIX + "h" + TaggedResult.LN + "b" + TaggedResult.LN + TaggedResult.SUFFIX + "h");
        // AND — the render is detected as a pure tagged block
        assertThat(TaggedResult.isTagged(rendered)).isTrue();
    }

    // R-TR-2
    @Test
    void pureTaggedBlockContentGetsNoHeader() {
        // GIVEN — a user message whose single TextContent is a pure tagged block
        var nl = System.lineSeparator();
        var block = TaggedResult.PREFIX + "ctx" + nl + "block-body" + nl + TaggedResult.SUFFIX + "ctx";

        // WHEN rendered
        String out = ChatMessageUtil.toString(UserMessage.from(block));

        // THEN — no USER: header, the block is rendered untouched
        assertThat(out).doesNotContain("USER:");
        assertThat(out).isEqualTo(block + nl);
    }

    // R-TR-2
    @Test
    void blockEmbeddedInToolResultIsNotPureAndKeepsItsHeader() {
        // GIVEN — a tool result whose text is a sender line plus an embedded tagged block
        var nl = System.lineSeparator();
        var block = TaggedResult.PREFIX + "async" + nl + "async-body" + nl + TaggedResult.SUFFIX + "async";
        var msg = ToolExecutionResultMessage.from("id", "shell", "sender-line" + nl + block);

        // WHEN rendered
        String out = ChatMessageUtil.toString(msg);

        // THEN — the whole text is not a pure block: the TOOL RESULT: header stays, the block stays full
        assertThat(out).contains("TOOL RESULT:").contains(block);
    }

    // R-TR-2
    @Test
    void multiContentUserMessageRendersOneEntryPerTextContent() {
        // GIVEN — a user message with a tagged block and real text as SEPARATE TextContents (D5)
        var nl = System.lineSeparator();
        var block = TaggedResult.PREFIX + "ctx" + nl + "block-body" + nl + TaggedResult.SUFFIX + "ctx";
        var msg = UserMessage.from(List.of(TextContent.from(block), TextContent.from("fix it")));

        // WHEN extracted
        var entries = ChatMessageUtil.extractMessages(msg);

        // THEN — exactly two entries: the block without header, the text with its own USER: header
        assertThat(entries).containsExactly(block, "USER:" + nl + "fix it");
        // AND — the render is the entries joined, the block first
        assertThat(ChatMessageUtil.toString(msg)).isEqualTo(block + nl + "USER:" + nl + "fix it" + nl);
    }

    // R-TR-2
    @Test
    void emptyBodyBlockIsPureAndGetsNoHeader() {
        // GIVEN — a block with an empty body (Start line, blank line, End line)
        var nl = System.lineSeparator();
        var block = TaggedResult.PREFIX + "h" + nl + nl + TaggedResult.SUFFIX + "h";

        // WHEN rendered as a user message
        String out = ChatMessageUtil.toString(UserMessage.from(block));

        // THEN — it is still a pure block: no header, the blank body line survives
        assertThat(out).doesNotContain("USER:");
        assertThat(out).isEqualTo(block + nl);
    }

    // R-TR-2
    @Test
    void singleLineStartWithoutEndIsNotTagged() {
        // GIVEN — one line starting with the prefix, no end line
        // WHEN checked
        boolean tagged = TaggedResult.isTagged(TaggedResult.PREFIX + "x");

        // THEN — no block (a block needs a first AND a last line)
        assertThat(tagged).isFalse();
        // AND — null and empty are never tagged
        assertThat(TaggedResult.isTagged(null)).isFalse();
        assertThat(TaggedResult.isTagged("")).isFalse();
    }

    // R-TR-2
    @Test
    void startWithoutSuffixLastLineIsNotTagged() {
        // GIVEN — text starting with the prefix whose last line is not the suffix
        // WHEN checked
        boolean tagged = TaggedResult.isTagged(TaggedResult.PREFIX + "x" + TaggedResult.LN + "body" + TaggedResult.LN + "no end");

        // THEN — not a pure block (the last line decides, not an endsWith over the whole text)
        assertThat(tagged).isFalse();
    }

    // R-TR-2
    @Test
    void innerEndLineDoesNotBreakPurity() {
        // GIVEN — a block whose BODY contains a line that itself starts with the suffix
        var block = TaggedResult.PREFIX + "h" + TaggedResult.LN
                + TaggedResult.SUFFIX + "fake" + TaggedResult.LN
                + "real body" + TaggedResult.LN
                + TaggedResult.SUFFIX + "h";

        // WHEN checked — the first and the last line decide
        boolean tagged = TaggedResult.isTagged(block);

        // THEN — still a pure block
        assertThat(tagged).isTrue();
        // AND — rendered as a user message it gets no header
        assertThat(ChatMessageUtil.toString(UserMessage.from(block))).doesNotContain("USER:");
    }

    // R21 — the estimator the live status uses for per-chunk token counting.

    @Test
    void estimates_32_chars_to_10() {
        // GIVEN a 32-char snippet (R21 BDD)
        // WHEN
        int tokens = ChatMessageUtil.estimateTokens("a".repeat(32));
        // THEN 32 / 3 = 10
        assertThat(tokens).isEqualTo(10);
    }

    @Test
    void estimates_26_chars_to_8() {
        // GIVEN a 26-char snippet (R21 BDD)
        // WHEN
        int tokens = ChatMessageUtil.estimateTokens("b".repeat(26));
        // THEN 26 / 3 = 8
        assertThat(tokens).isEqualTo(8);
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
    void boundary_six_chars_is_two() {
        // GIVEN a snippet of exactly 6 chars — first length past the short floor
        // WHEN
        int tokens = ChatMessageUtil.estimateTokens("123456");
        // THEN 6 / 3 = 2
        assertThat(tokens).isEqualTo(2);
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

    /** Inverse of the chars/3 estimator — one place for the token math (R-CC-13, docs/compact.md). */
    @Test
    void tokensToChars_invertsTheCharsToTokensEstimator() {
        // WHEN — tokens are converted back to chars
        assertThat(ChatMessageUtil.tokensToChars(100)).isEqualTo(300);
        // AND — zero stays zero
        assertThat(ChatMessageUtil.tokensToChars(0)).isZero();
        // AND — the multiplication is clamped against integer overflow
        assertThat(ChatMessageUtil.tokensToChars(Integer.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
    }
}
