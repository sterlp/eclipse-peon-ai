package org.sterl.llmpeon.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sterl.llmpeon.memory.ThreadSafeMemory;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;

/** The renderer reads a mixed user message by position and tag — literal fixtures, no builder (R-TR-10/11, R-RP-4). */
@Timeout(1)
class ChatMessageInputRenderingTest {

    private static final String NL = System.lineSeparator();

    private static String block(String head, String body) {
        return "//-- Start " + head + NL + body + NL + "//-- End " + head;
    }

    private static TextContent pictureHead(String path) {
        return TextContent.from(block("picture: " + path, "attached by the user"));
    }

    private static ImageContent image(String base64) {
        return ImageContent.from(base64, "image/png", ImageContent.DetailLevel.HIGH);
    }

    // R-TR-10
    // R-TR-11
    // R-RP-4
    // UC-RP-5
    @Test
    void multiplePicturesUseOnlyTheirAdjacentHeads() {
        // GIVEN — text, file, head A + image A, head B + image B with different paths and bytes
        var fileBlock = block("file: notes.txt", "notes-body");
        var message = UserMessage.from(List.<Content>of(
                TextContent.from("look at these"), TextContent.from(fileBlock),
                pictureHead("/work/a.png"), image("AAAA"),
                pictureHead("/work/b.png"), image("BBBB")));

        // WHEN
        var rendered = ChatMessageUtil.extractMessages(message);

        // THEN — every image names its OWN head (not the first one), no image bytes anywhere
        assertThat(List.copyOf(rendered)).containsExactly(
                "USER:" + NL + "look at these",
                fileBlock,
                block("picture: /work/a.png", "attached by the user"),
                "[picture removed for compact: /work/a.png]",
                block("picture: /work/b.png", "attached by the user"),
                "[picture removed for compact: /work/b.png]");
    }

    // R-TR-10
    // R-RP-4
    @Test
    void orphanImageUsesUnknownInsteadOfAnEarlierHead() {
        // GIVEN — head A, an intermediate text, then an image (head not adjacent) and one more image without any head
        var message = UserMessage.from(List.<Content>of(
                pictureHead("/work/a.png"), TextContent.from("in between"),
                image("AAAA"), image("BBBB")));

        // WHEN
        var rendered = ChatMessageUtil.extractMessages(message);

        // THEN — neither image reuses head A
        assertThat(List.copyOf(rendered)).containsExactly(
                block("picture: /work/a.png", "attached by the user"),
                "USER:" + NL + "in between",
                "[picture removed for compact: unknown]",
                "[picture removed for compact: unknown]");
    }

    // R-TR-11
    @Test
    void multiContentRenderingAndMemoryContainsDoNotUseSingleText() {
        // GIVEN — a message with two file attachments in one memory
        var one = block("file: one.txt", "ALPHA-body");
        var two = block("file: two.txt", "BETA-body");
        var message = UserMessage.from(List.<Content>of(
                TextContent.from("question"), TextContent.from(one), TextContent.from(two)));
        var memory = new ThreadSafeMemory().add(message);

        // WHEN / THEN — every content is rendered in order
        assertThat(List.copyOf(ChatMessageUtil.extractMessages(message)))
                .containsExactly("USER:" + NL + "question", one, two);
        // AND the dedup query finds each body — also the second one (no first-text-only lookup)
        assertThat(memory.containsUserMessage("ALPHA-body")).isTrue();
        assertThat(memory.containsUserMessage("BETA-body")).isTrue();
        assertThat(memory.containsUserMessage("GAMMA-body")).isFalse();
    }
}
