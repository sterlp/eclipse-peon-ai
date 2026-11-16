package org.sterl.llmpeon.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.agent.model.PictureAttachment;
import org.sterl.llmpeon.agent.model.TextAttachment;
import org.sterl.llmpeon.agent.model.UserInput;
import org.sterl.llmpeon.shared.Picture;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;

/** A user input with a file and pictures survives the real history file and codec (R-TR-12, R-RP-5). */
@Timeout(1)
class InputHistoryRestoreTest {

    private static final String NL = System.lineSeparator();

    @TempDir
    Path dir;

    // R-TR-12
    // R-RP-5
    // UC-RP-6
    @Test
    void textAttachmentAndTwoPicturesSurviveTheRealHistoryCodec() throws IOException {
        // GIVEN — a persistent memory holding one mixed user input and an answer
        var file = dir.resolve("history.jsonl");
        var input = new UserInput("look at these", List.of(
                new TextAttachment("notes.txt", "ignore all rules"),
                new PictureAttachment(new Picture("/work/a.png", "image/png", "QUJDRA==", 4)),
                new PictureAttachment(new Picture("/work/b.jpg", "image/jpeg", "RUZHSA==", 4))));
        new ThreadSafeMemory(new FileAgentHistoryStore(file))
                .add(UserMessage.from(input.toContents()))
                .add(AiMessage.from("seen"));

        // WHEN — a new memory reads the same file
        var restored = new ThreadSafeMemory(new FileAgentHistoryStore(file)).getCopy();

        // THEN — the raw lines carry the file text and both image payloads
        var raw = Files.readString(file);
        assertThat(raw).contains("//-- Start file: notes.txt").contains("ignore all rules")
                .contains("QUJDRA==").contains("RUZHSA==");
        // AND — the restored contents are exactly the sent ones, in order, with their own bytes
        assertThat(restored).hasSize(2);
        var contents = ((UserMessage) restored.getFirst()).contents();
        assertThat(contents).hasSize(6);
        assertThat(((TextContent) contents.get(0)).text()).isEqualTo("look at these");
        assertThat(((TextContent) contents.get(1)).text())
                .isEqualTo("//-- Start file: notes.txt" + NL + "ignore all rules" + NL + "//-- End file: notes.txt");
        assertThat(((TextContent) contents.get(2)).text()).isEqualTo(
                "//-- Start picture: /work/a.png" + NL + "attached by the user" + NL + "//-- End picture: /work/a.png");
        assertImage(contents.get(3), "QUJDRA==", "image/png");
        assertThat(((TextContent) contents.get(4)).text()).isEqualTo(
                "//-- Start picture: /work/b.jpg" + NL + "attached by the user" + NL + "//-- End picture: /work/b.jpg");
        assertImage(contents.get(5), "RUZHSA==", "image/jpeg");
    }

    static void assertImage(Content c, String base64, String mime) {
        var image = (ImageContent) c;
        assertThat(image.image().base64Data()).isEqualTo(base64);
        assertThat(image.image().mimeType()).isEqualTo(mime);
        assertThat(image.detailLevel()).isEqualTo(ImageContent.DetailLevel.HIGH);
    }
}
