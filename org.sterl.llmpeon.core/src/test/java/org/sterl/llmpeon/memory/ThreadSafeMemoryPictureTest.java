package org.sterl.llmpeon.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.AbstractMemoryFileTest;
import org.sterl.llmpeon.shared.Picture;
import org.sterl.llmpeon.shared.PictureMessage;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

class ThreadSafeMemoryPictureTest extends AbstractMemoryFileTest {

    @Test
    void twoPicturesGetOneUserMessageEachBehindAllResults() {
        // GIVEN
        var memory = new ThreadSafeMemory();
        var img1 = PictureMessage.of(new Picture(tmp.resolve("a.png").toString(), "image/png", "AAAA", 4));
        var img2 = PictureMessage.of(new Picture(tmp.resolve("b.png").toString(), "image/png", "BBBB", 4));

        // WHEN
        memory.addResult(response(), results(), List.of(img1, img2));

        // THEN — AI(calls) → R → R → ok → U(img1) → ok → U(img2), no joined image message
        assertThat(memory.messageFlow()).isEqualTo(
                "TOOL_REQUEST->TOOL_EXECUTION_RESULT->TOOL_EXECUTION_RESULT->AI->USER->AI->USER");
        var all = memory.getCopy();
        assertThat(all.get(3)).isEqualTo(AiMessage.from("ok"));
        assertThat(all.get(4)).isSameAs(img1);
        assertThat(all.get(5)).isEqualTo(AiMessage.from("ok"));
        assertThat(all.get(6)).isSameAs(img2);
    }

    @Test
    void withoutFollowUpsTheRoundIsUnchanged() {
        var memory = new ThreadSafeMemory();

        memory.addResult(response(), results());

        assertThat(memory.messageFlow()).isEqualTo("TOOL_REQUEST->TOOL_EXECUTION_RESULT->TOOL_EXECUTION_RESULT");
    }

    // UC-RP-6
    @Test
    void pictureSurvivesTheHistoryRoundTripWithIdenticalBytes() throws IOException {
        // GIVEN — a persistent memory that holds a picture round
        var dir = Files.createDirectory(tmp.resolve(UUID.randomUUID().toString()));
        var store = new FileAgentHistoryStore(dir.resolve("history.jsonl"));
        var memory = new ThreadSafeMemory(store);
        var picture = new Picture(tmp.resolve("ui/login.png").toString(), "image/png", "iVBORw0KGgo=", 8);
        memory.addResult(response(), results(), List.of(PictureMessage.of(picture)));

        // WHEN — restore into a new memory
        var restored = new ThreadSafeMemory(new FileAgentHistoryStore(dir.resolve("history.jsonl"))).getCopy();

        // THEN
        assertThat(restored).hasSize(memory.size());
        var image = images(restored.getLast());
        assertThat(image).hasSize(1);
        assertThat(image.getFirst().image().base64Data()).isEqualTo("iVBORw0KGgo=");
        assertThat(image.getFirst().image().mimeType()).isEqualTo("image/png");
        assertThat(image.getFirst().detailLevel()).isEqualTo(ImageContent.DetailLevel.HIGH);

        assertThat(PictureMessage.pathOf((UserMessage) restored.getLast())).isEqualTo(picture.displayPath());
    }

    private static List<ImageContent> images(ChatMessage m) {
        return ((UserMessage) m).contents().stream()
                .filter(c -> c instanceof ImageContent).map(c -> (ImageContent) c).toList();
    }

    private static ChatResponse response() {
        return ChatResponse.builder().aiMessage(AiMessage.builder().toolExecutionRequests(List.of(
                ToolExecutionRequest.builder().id("1").name("diskReadPicture").arguments("{}").build(),
                ToolExecutionRequest.builder().id("2").name("diskReadPicture").arguments("{}").build())).build()).build();
    }

    private static List<ToolExecutionResultMessage> results() {
        return List.of(ToolExecutionResultMessage.from("1", "diskReadPicture", "📷 a"),
                ToolExecutionResultMessage.from("2", "diskReadPicture", "📷 b"));
    }
}
