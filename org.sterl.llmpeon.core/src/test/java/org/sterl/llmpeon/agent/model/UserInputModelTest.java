package org.sterl.llmpeon.agent.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sterl.llmpeon.shared.Picture;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;

/** What the human sends: typed text first (untouched), then the attachments in order. */
@Timeout(1)
class UserInputModelTest {

    private static final String NL = System.lineSeparator();
    private static final String PNG_BASE64 = "iVBORw0KGgoAAAANSUhEUg==";
    private static final Picture B_PNG = new Picture("b.png", "image/png", PNG_BASE64, 18);
    private static final String FILE_BLOCK =
            "//-- Start file: a.txt" + NL + "hello" + NL + "//-- End file: a.txt";
    private static final String PICTURE_HEAD =
            "//-- Start picture: b.png" + NL + "attached by the user" + NL + "//-- End picture: b.png";

    // R-TR-11
    @Test
    void contentsPreserveTextBeforeAllAttachments() {
        var input = new UserInput(" look at this" + NL,
                List.of(new TextAttachment("a.txt", "hello"), new PictureAttachment(B_PNG)));

        assertThat(input.toContents()).containsExactly(
                TextContent.from(" look at this" + NL),
                TextContent.from(FILE_BLOCK),
                TextContent.from(PICTURE_HEAD),
                ImageContent.from(PNG_BASE64, "image/png", ImageContent.DetailLevel.HIGH));
    }

    // R-TR-11
    @Test
    void nullAndBlankTextNeverCreateAnEmptyTextContent() {
        assertThat(UserInput.of(null).toContents()).isEmpty();
        assertThat(UserInput.of("").toContents()).isEmpty();
        assertThat(UserInput.of("  " + NL).toContents()).isEmpty();
        assertThat(UserInput.of("hi").toContents()).containsExactly(TextContent.from("hi"));
        // attachments survive without text
        assertThat(new UserInput(null, List.of(new TextAttachment("a.txt", "hello"))).toContents())
                .containsExactly(TextContent.from(FILE_BLOCK));
        assertThat(new UserInput("   ", List.of(new PictureAttachment(B_PNG))).toContents()).containsExactly(
                TextContent.from(PICTURE_HEAD),
                ImageContent.from(PNG_BASE64, "image/png", ImageContent.DetailLevel.HIGH));
    }

    // R-TR-11
    @Test
    void attachmentListIsDefensivelyCopied() {
        var arrange = new ArrayList<Attachment>(List.of(new TextAttachment("a.txt", "hello")));
        var input = new UserInput("hi", arrange);

        arrange.add(new PictureAttachment(B_PNG));

        assertThat(input.attachments()).hasSize(1);
        assertThat(input.toContents()).containsExactly(TextContent.from("hi"), TextContent.from(FILE_BLOCK));
        assertThatThrownBy(() -> input.attachments().add(new PictureAttachment(B_PNG)))
                .isExactlyInstanceOf(UnsupportedOperationException.class);
        assertThat(UserInput.of("x").attachments()).isEmpty();
    }

    // R-TR-10
    // R-TR-11
    @Test
    void nullAttachmentsAndMissingRequiredBlockDataAreRejected() {
        assertThatThrownBy(() -> new UserInput("hi", null)).isExactlyInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UserInput("hi", Arrays.asList((Attachment) null)))
                .isExactlyInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TextAttachment(null, "body")).isExactlyInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TextAttachment("a.txt", null)).isExactlyInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PictureAttachment(null)).isExactlyInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ContextInput(null, "body")).isExactlyInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ContextInput("context: x", null)).isExactlyInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HarnessInput(null, "body")).isExactlyInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HarnessInput("h", null)).isExactlyInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HarnessInput((Picture) null)).isExactlyInstanceOf(NullPointerException.class);
    }
}
