package org.sterl.llmpeon.compact;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sterl.llmpeon.model.CompactResult.Stage;
import org.sterl.llmpeon.queuedmessages.UserMessageQueue;
import org.sterl.llmpeon.tool.ToolService;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * Compact staging of user input with attachments (R-CIB-4, R-TR-9/11/12, R-RP-4): real stager,
 * mixed user messages, literal expectations. Real user text = untagged non-marker text, by tag.
 */
@Timeout(1)
class ContextTrimInputTest {

    private static final String NL = System.lineSeparator();
    private final ContextTrimComponent subject = new ContextTrimComponent();

    private static String block(String head, String body) {
        return "//-- Start " + head + NL + body + NL + "//-- End " + head;
    }

    private static String file(String name, String body) {
        return block("file: " + name, body);
    }

    private static String pictureHead(String path) {
        return block("picture: " + path, "attached by the user");
    }

    private static String placeholder(String path) {
        return "[picture removed for compact: " + path + "]";
    }

    private static UserMessage user(Object... parts) {
        var contents = new java.util.ArrayList<Content>();
        for (var p : parts) {
            contents.add(p instanceof Content c ? c : TextContent.from((String) p));
        }
        return UserMessage.from(contents);
    }

    private static ImageContent image(String base64) {
        return ImageContent.from(base64, "image/png", ImageContent.DetailLevel.HIGH);
    }

    private static AiMessage ai(String text, String thinking) {
        return AiMessage.builder().text(text).thinking(thinking).build();
    }

    // R-TR-9
    // R-TR-12
    // R-RP-4
    // UC-RP-5
    @Test
    void offAndUnderBudgetKeepFilesDropContextsAndReplacePictures() {
        // GIVEN — one message: context block, question, file, picture head + image
        var context = block("context: memory", "memory-body");
        var message = user(context, "question", file("notes.txt", "notes-body"),
                pictureHead("/work/a.png"), image("AAAA"));
        var expected = "USER:" + NL + "question" + NL
                + file("notes.txt", "notes-body") + NL
                + placeholder("/work/a.png") + NL
                + "session truncated" + NL + "context blocks dropped: 2";

        // WHEN — budget off and a generous budget (both leave the survivors uncut)
        var off = subject.stage(List.of(message), 0);
        var under = subject.stage(List.of(message), 100000);

        // THEN — the file stays, context block and the picture head drop (counted), no image data
        for (var outcome : List.of(off, under)) {
            assertThat(outcome.stage()).isEqualTo(Stage.NONE);
            assertThat(outcome.contextBlocksDropped()).isEqualTo(2);
            assertThat(outcome.input()).isEqualTo(expected);
        }
    }

    // R-TR-11
    // R-TR-12
    // R-CIB-4
    @Test
    void latestRealUserIsFoundBeforeFileAndPictureAttachments() {
        // GIVEN — an old question, a long thinking turn, the latest question followed by file and picture,
        // then a pure tool picture; the budget fits stage 1
        var messages = List.<ChatMessage>of(
                user("old-question " + "A".repeat(2000)),
                ai("a1", "T".repeat(15000)),
                user("latest-question " + "B".repeat(2000), file("n.txt", "n-body"),
                        pictureHead("/work/a.png"), image("AAAA")),
                user(pictureHead("/work/tool.png"), image("CCCC")));

        // WHEN
        var outcome = subject.stage(messages, 5200);

        // THEN — the latest question is the full one (not the old one), file and placeholders survive
        assertThat(outcome.stage()).isEqualTo(Stage.THINK_AND_USER);
        assertThat(outcome.input()).isEqualTo(
                "AI:" + NL + "a1" + NL
                + "THINK:" + NL + "…$ " + "T".repeat(9000) + NL
                + "USER:" + NL + "latest-question " + "B".repeat(2000) + NL
                + file("n.txt", "n-body") + NL
                + placeholder("/work/a.png") + NL
                + placeholder("/work/tool.png") + NL
                + "session truncated" + NL + "thinking capped 9000 (front)" + NL + "context blocks dropped: 2");
    }

    // R-TR-12
    // R-CIB-4
    // R-RP-4
    @Test
    void earlierFilesAndPicturePlaceholdersSurviveStagesOneAndTwo() {
        // GIVEN — an earlier message with question, file and picture; a new question at the end
        var earlier = user("EARLIER-TEXT", file("n.txt", "n-body"), pictureHead("/work/a.png"), image("AAAA"));
        var stage1 = List.<ChatMessage>of(earlier, ai("a", "T".repeat(15000)), user("NEW-QUESTION"));
        var stage2 = List.<ChatMessage>of(earlier, ai("a", "T".repeat(15000)),
                ToolExecutionResultMessage.from("id", "read", "R".repeat(9000)), user("NEW-QUESTION"));
        var kept = file("n.txt", "n-body") + NL + placeholder("/work/a.png") + NL;

        // WHEN
        var one = subject.stage(stage1, 3500);
        var two = subject.stage(stage2, 5000);

        // THEN stage 1 — the earlier text is gone, file and placeholder stay
        assertThat(one.stage()).isEqualTo(Stage.THINK_AND_USER);
        assertThat(one.input()).isEqualTo(kept
                + "AI:" + NL + "a" + NL + "THINK:" + NL + "…$ " + "T".repeat(9000) + NL
                + "USER:" + NL + "NEW-QUESTION" + NL
                + "session truncated" + NL + "thinking capped 9000 (front)" + NL + "context blocks dropped: 1");
        // THEN stage 2 — same for the earlier message, tools and thinking capped at 6000
        assertThat(two.stage()).isEqualTo(Stage.TOOL_RESULTS);
        assertThat(two.input()).isEqualTo(kept
                + "AI:" + NL + "a" + NL + "THINK:" + NL + "…$ " + "T".repeat(6000) + NL
                + "TOOL RESULT:" + NL + "tool name: read" + NL + "result:" + NL + "R".repeat(6000) + NL
                + "USER:" + NL + "NEW-QUESTION" + NL
                + "session truncated" + NL + "thinking capped 6000 (front), tool results 6000" + NL
                + "context blocks dropped: 1");
    }

    // R-CIB-4
    // R-CC-9
    // R-TR-11
    @Test
    void allThreeMarkersStayStateAndNeverClaimTheLastUserSlot() {
        // GIVEN — per marker: the real question first, a long thinking turn, then a message holding the marker
        var markers = List.of(
                ToolService.COMPACT_HINT + " (now)",
                UserMessageQueue.QUEUED_MARKER_PREFIX + "queued text",
                CompactConstants.REINSERT_MARKER + NL + NL + "OLD SUMMARY");
        for (var marker : markers) {
            var messages = List.<ChatMessage>of(
                    user("real-question " + "D".repeat(1000)),
                    ai("a", "T".repeat(15000)),
                    user(marker));

            // WHEN
            var outcome = subject.stage(messages, 3500);

            // THEN — the real question stays the full user turn, the marker text is state and falls out
            assertThat(outcome.stage()).as(marker).isEqualTo(Stage.THINK_AND_USER);
            assertThat(outcome.input()).as(marker).isEqualTo(
                    "USER:" + NL + "real-question " + "D".repeat(1000) + NL
                    + "AI:" + NL + "a" + NL + "THINK:" + NL + "…$ " + "T".repeat(9000) + NL
                    + "session truncated" + NL + "thinking capped 9000 (front)");
        }
    }

    // R-CIB-4
    // R-CIB-5
    // R-TR-12
    @Test
    void finalCapAlsoAppliesToLargeFileAttachments() {
        // GIVEN — an earlier message with a 9000-char file and a picture, a thinking turn, a new question
        var messages = List.<ChatMessage>of(
                user("EARLIER-TEXT", file("big.txt", "F".repeat(9000)), pictureHead("/work/a.png"), image("AAAA")),
                ai("a", "T".repeat(15000)),
                user("NEW-QUESTION"));

        // WHEN — a budget no earlier stage can reach
        var outcome = subject.stage(messages, 1000);

        // THEN — the per-message cap applies to the file block too; the placeholder is cut with it
        // (disclosed cap 962 chars per message: 25-char file head line + 937 body chars, 16 chars + 946 thinking chars)
        assertThat(outcome.stage()).isEqualTo(Stage.PER_MESSAGE);
        assertThat(outcome.estimateAfter()).isLessThanOrEqualTo(1000);
        assertThat(outcome.contextBlocksDropped()).isEqualTo(1);
        assertThat(outcome.input()).isEqualTo(
                "//-- Start file: big.txt" + NL + "F".repeat(937) + NL
                + "AI:" + NL + "a" + NL + "THINK:" + NL + "…$ " + "T".repeat(946) + NL
                + "USER:" + NL + "NEW-QUESTION" + NL
                + "session truncated" + NL
                + "thinking capped 6000 (front), tool results 6000, per-message cap 962" + NL
                + "context blocks dropped: 1");
    }
}
