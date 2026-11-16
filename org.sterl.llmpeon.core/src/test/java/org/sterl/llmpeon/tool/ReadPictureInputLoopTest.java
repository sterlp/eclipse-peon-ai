package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.tool.tools.DiskFileReadTool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

/** The picture tool's round through the real disk loader and the real loop — exact wire contents. */
@Timeout(1)
class ReadPictureInputLoopTest {

    private static final String NL = System.lineSeparator();

    @TempDir
    Path tmp;

    final ToolService subject = new ToolService(false);

    @BeforeEach
    void setUp() throws IOException {
        subject.addTool(new DiskFileReadTool(tmp));
        Files.write(tmp.resolve("login.png"), new byte[] { 1, 2, 3 });
        Files.writeString(tmp.resolve("notes.txt"), "hello");
    }

    // R-TR-10
    // R-RP-1
    // UC-RP-1
    // UC-RP-2
    @Test
    void realDiskPictureUsesTheSingleHarnessConstructionPath() {
        var abs = tmp.resolve("login.png").toAbsolutePath().normalize().toString();

        var run = run(call("1", "diskReadPicture", "{\"filePath\":\"login.png\"}"),
                call("2", "diskReadFile", "{\"filePath\":\"notes.txt\"}"));

        // THEN — both results first, then ok, then ONE picture message: exact head text, then the image
        var sent = run.secondRequest();
        assertThat(sent.stream().map(m -> m.type().name()).toList()).containsExactly(
                "USER", "AI", "TOOL_EXECUTION_RESULT", "TOOL_EXECUTION_RESULT", "AI", "USER");
        assertThat(((ToolExecutionResultMessage) sent.get(2)).text())
                .isEqualTo("📷 " + abs + " (0.0 KB) — picture follows in the next user message");
        assertThat(((ToolExecutionResultMessage) sent.get(3)).text()).isEqualTo("   1: hello\n");
        assertThat(((AiMessage) sent.get(4)).text()).isEqualTo("ok");
        assertThat(((UserMessage) sent.get(5)).contents()).containsExactly(
                TextContent.from("//-- Start picture: " + abs + NL
                        + "added by the picture tool, not typed by the user" + NL
                        + "//-- End picture: " + abs),
                ImageContent.from("AQID", "image/png", ImageContent.DetailLevel.HIGH));
    }

    // R-RP-3
    // UC-RP-4
    @Test
    void failedPictureLoadCreatesNoAfterResultsInput() {
        var notes = tmp.resolve("notes.txt").toAbsolutePath().normalize().toString();

        var wrongType = run(call("1", "diskReadPicture", "{\"filePath\":\"notes.txt\"}"));
        var missing = run(call("1", "diskReadPicture", "{\"filePath\":\"nope.png\"}"));

        // THEN — exactly the text failure as result, no ok/picture sequence, nothing queued behind the results
        assertThat(wrongType.secondRequest().stream().map(m -> m.type().name()).toList())
                .containsExactly("USER", "AI", "TOOL_EXECUTION_RESULT");
        assertThat(((ToolExecutionResultMessage) wrongType.secondRequest().get(2)).text())
                .isEqualTo("unsupported picture type: .txt (png, jpg, jpeg, gif, webp) — " + notes);
        assertThat(wrongType.request().drainAfterResults()).isEmpty();

        assertThat(missing.secondRequest().stream().map(m -> m.type().name()).toList())
                .containsExactly("USER", "AI", "TOOL_EXECUTION_RESULT");
        assertThat(((ToolExecutionResultMessage) missing.secondRequest().get(2)).text())
                .isEqualTo("File not found: nope.png also not in " + tmp);
        assertThat(missing.request().drainAfterResults()).isEmpty();
    }

    private record Run(List<ChatMessage> secondRequest, ToolLoopRequest request) {}

    private Run run(ToolExecutionRequest... calls) {
        var round = new AtomicInteger();
        var second = new AtomicReference<List<ChatMessage>>();
        var cm = mockWithHandler(req -> {
            if (round.incrementAndGet() == 1) {
                return ChatResponse.builder().aiMessage(AiMessage.builder()
                        .toolExecutionRequests(List.of(calls)).build()).build();
            }
            second.set(req.messages());
            return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
        });
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("look at login.png"));
        var request = ToolLoopRequest.builder().memory(memory)
                .chatModel(new ConfiguredChatModel(LlmConfig.newOpenAi("foo"), cm)).build();

        subject.executeLoop(request);

        return new Run(second.get(), request);
    }

    private static ToolExecutionRequest call(String id, String name, String args) {
        return ToolExecutionRequest.builder().id(id).name(name).arguments(args).build();
    }

    private static StreamingChatModel mockWithHandler(Function<ChatRequest, ChatResponse> fn) {
        var cm = mock(StreamingChatModel.class);
        doAnswer(inv -> {
            var cr = fn.apply(inv.getArgument(0, ChatRequest.class));
            inv.getArgument(1, StreamingChatResponseHandler.class).onCompleteResponse(cr);
            return null;
        }).when(cm).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        return cm;
    }
}
