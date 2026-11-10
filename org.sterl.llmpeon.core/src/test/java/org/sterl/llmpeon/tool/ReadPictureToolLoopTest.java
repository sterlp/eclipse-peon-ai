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
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.shared.PictureMessage;
import org.sterl.llmpeon.tool.tools.DiskFileReadTool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

/** R-RP-1/3 through the real loop: tool → {@code ToolLoopRequest} → {@code ToolService.executeLoop} → memory. */
class ReadPictureToolLoopTest {

    @TempDir
    Path tmp;

    final ToolService subject = new ToolService(false);

    @BeforeEach
    void setUp() throws IOException {
        subject.addTool(new DiskFileReadTool(tmp));
        Files.write(tmp.resolve("login.png"), new byte[] { 1, 2, 3 });
        Files.writeString(tmp.resolve("notes.txt"), "hello");
    }

    // UC-RP-1
    @Test
    void pictureArrivesAsUserMessageBehindTheResultAfterAnOk() {
        // WHEN
        var run = run(call("1", "diskReadPicture", "{\"filePath\":\"login.png\"}"));

        // THEN — the 2nd model request: User → AI(call) → Result → AI(ok) → User(head + image)
        var sent = run.secondRequest();
        assertThat(flow(sent)).isEqualTo("USER->AI->TOOL_EXECUTION_RESULT->AI->USER");
        assertThat(((ToolExecutionResultMessage) sent.get(2)).text())
                .startsWith("📷 ").contains("login.png").contains("picture follows in the next user message");
        assertThat(((AiMessage) sent.get(3)).text()).isEqualTo("ok");
        var pictureMessage = (UserMessage) sent.get(4);
        assertThat(PictureMessage.pathOf(pictureMessage)).endsWith("login.png");
        assertThat(images(pictureMessage)).hasSize(1);
        assertThat(images(pictureMessage).getFirst().detailLevel()).isEqualTo(ImageContent.DetailLevel.HIGH);
    }

    // UC-RP-2
    @Test
    void mixedRoundKeepsAllResultsDirectlyBehindTheCallsBeforeThePicture() {
        // WHEN
        var run = run(
                call("1", "diskListDirectory", "{}"),
                call("2", "diskReadPicture", "{\"filePath\":\"login.png\"}"),
                call("3", "diskReadFile", "{\"filePath\":\"notes.txt\"}"));

        // THEN — three results without a gap, only then ok + picture
        assertThat(flow(run.secondRequest())).isEqualTo(
                "USER->AI->TOOL_EXECUTION_RESULT->TOOL_EXECUTION_RESULT->TOOL_EXECUTION_RESULT->AI->USER");
        assertThat(images(run.secondRequest().getLast())).hasSize(1);
    }

    // UC-RP-4
    @Test
    void invalidPicturesGiveOnlyATextResultAndNoImageSequence() {
        // WHEN — a wrong type
        var wrongType = run(call("1", "diskReadPicture", "{\"filePath\":\"notes.txt\"}"));

        // THEN
        assertThat(((ToolExecutionResultMessage) wrongType.secondRequest().get(2)).text())
                .startsWith("unsupported picture type: .txt (png, jpg, jpeg, gif, webp) — ");
        assertThat(flow(wrongType.secondRequest())).isEqualTo("USER->AI->TOOL_EXECUTION_RESULT");
        assertThat(imageCount(wrongType.memory())).isZero();

        // WHEN — a missing file
        var missing = run(call("1", "diskReadPicture", "{\"filePath\":\"nope.png\"}"));

        // THEN
        assertThat(((ToolExecutionResultMessage) missing.secondRequest().get(2)).text())
                .startsWith("File not found: nope.png");
        assertThat(imageCount(missing.memory())).isZero();
    }

    @Test
    void aSubRequestNeverFeedsTheRoundOfItsParent() {
        // GIVEN — a parent request with a queued picture
        var parent = ToolLoopRequest.builder().memory(new ThreadSafeMemory())
                .chatModel(new ConfiguredChatModel(LlmConfig.newOpenAi("foo"), mock(StreamingChatModel.class))).build();
        parent.appendAfterResults(UserMessage.from("queued"));

        // WHEN — the sub-agent request is derived via toBuilder
        var sub = parent.toBuilder().memory(new ThreadSafeMemory()).build();
        sub.appendAfterResults(UserMessage.from("sub"));

        // THEN — each owns its list
        assertThat(sub.drainAfterResults()).extracting(m -> ((UserMessage) m).singleText()).containsExactly("sub");
        assertThat(parent.drainAfterResults()).extracting(m -> ((UserMessage) m).singleText()).containsExactly("queued");
        assertThat(parent.drainAfterResults()).isEmpty();
    }

    private record Run(List<ChatMessage> secondRequest, ThreadSafeMemory memory) {}

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

        subject.executeLoop(ToolLoopRequest.builder().memory(memory)
                .chatModel(new ConfiguredChatModel(LlmConfig.newOpenAi("foo"), cm)).build());

        return new Run(second.get(), memory);
    }

    private static ToolExecutionRequest call(String id, String name, String args) {
        return ToolExecutionRequest.builder().id(id).name(name).arguments(args).build();
    }

    private static String flow(List<ChatMessage> messages) {
        return String.join("->", messages.stream().map(m -> m.type().name()).toList());
    }

    private static List<ImageContent> images(ChatMessage m) {
        return ((UserMessage) m).contents().stream()
                .filter(c -> c instanceof ImageContent).map(c -> (ImageContent) c).toList();
    }

    private static long imageCount(ThreadSafeMemory memory) {
        return memory.getCopy().stream()
                .filter(UserMessage.class::isInstance).mapToLong(m -> images(m).size()).sum();
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
