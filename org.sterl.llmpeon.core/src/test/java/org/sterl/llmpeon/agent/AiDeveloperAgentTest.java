package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.context.SimpleContextItem;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.tools.CompactSessionTool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;

public class AiDeveloperAgentTest {

    final ToolService toolService = new ToolService();
    AiDevAgent subject;
    StreamingChatModel cm;
    final AtomicReference<Function<ChatRequest, ChatResponse>> fn = new AtomicReference<>();
    
    public static final AiMessage CALL_ME = AiMessage.builder()
            .toolExecutionRequests(Arrays.asList(ToolExecutionRequest.builder().name(CompactSessionTool.NAME).build()))
            .build();
    /*
    class ClearMemoryTestTool implements SmartTool {
        public static final AiMessage CALL_ME = AiMessage.builder()
                .toolExecutionRequests(Arrays.asList(ToolExecutionRequest.builder().name("clearMemory").build()))
                .build();
        ToolLoopRequest request;
        @Tool(name = "clearMemory")
        public String hello() {
            request.getMemory().clear();
            return "Hello from clearMemory";
        }

        @Override
        public void withToolRequest(ToolLoopRequest request) {
            this.request = request;
        }
    }*/
    
    @BeforeEach
    void beforeEach() {
        toolService.replaceTool(new CompactSessionTool());
        cm = mockWithHandler();
        subject = new AiDevAgent(new ConfiguredChatModel(LlmConfig.newOllama("foo"), cm), toolService);
    }
    
    @Test
    void test_simple_call() {
        // GIVEN
        var aiMessage = AiMessage.aiMessage("Okay thats good");
        var requestRef  = new AtomicReference<ChatRequest>();
        
        fn.set(req -> {
            requestRef.set(req);
            return ChatResponse.builder().aiMessage(aiMessage).build();
        });
        
        // WHEN
        subject.call("Foo", null);

        // THEN
        verify(cm, times(1)).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        var mem = subject.getMemory().getCopy();
        assertThat(((UserMessage)mem.get(0)).singleText()).contains("Foo");
        assertThat(mem.get(1)).isEqualTo(aiMessage);
        // AND
        assertThat(requestRef.get().messages().get(0).getClass()).isEqualTo(SystemMessage.class);
    }
    
    @Test
    void test_clear_memory() {
        // GIVEN
        for (int i = 0; i < 5; i++) {
            subject.addMessage(UserMessage.from("Foo " + i));
            subject.addMessage(AiMessage.from("Foo " + i));
        }
        var requestRef  = new AtomicReference<ChatRequest>();
        var clear = new AtomicBoolean(true);

        fn.set(req -> {
            requestRef.set(req);
            if (clear.getAndSet(false)) return ChatResponse.builder()
                    .aiMessage(CALL_ME)
                    .build();

            return ChatResponse.builder().aiMessage(AiMessage.aiMessage("Okay thats good")).build();
        });

        // WHEN
        subject.call("Foo", null);

        // THEN
        verify(cm, times(3)).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        // AND — memory structure after delegated compact:
        // [0] UserMessage "Session compacted..." (resume message)
        // [1] AiMessage "Okay thats good" (compressor's summary, added by compressContext)
        // [2] CALL_ME (tool request, added by tool loop)
        // [3] ToolExecutionResultMessage (tool result, added by tool loop)
        // [4] AiMessage "Okay thats good" (final response, second iteration)
        var mem = subject.getMemory().getCopy();
        assertThat(((UserMessage)mem.get(0)).singleText()).contains("Session compacted");
        assertThat(mem.get(1)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage)mem.get(1)).text()).contains("Okay thats good");
        assertThat(mem.get(2)).isEqualTo(CALL_ME);
        assertThat(mem.get(3)).isInstanceOf(ToolExecutionResultMessage.class);
        assertThat(((ToolExecutionResultMessage)mem.get(3)).text()).contains("Okay thats good");
    }
    
    @Test
    void test_inLoopCompact_tokenCounterResetsBelowThreshold() {
        // GIVEN — a tiny budget (1000) so the stale pre-compact usage (9600) sits far above it
        var config = LlmConfig.newOllama("foo").toBuilder().autoCompactAfter(1000).build();
        subject = new AiDevAgent(new ConfiguredChatModel(config, cm), toolService);
        for (int i = 0; i < 5; i++) {
            subject.addMessage(UserMessage.from("Foo " + i));
            subject.addMessage(AiMessage.from("Bar " + i));
        }
        var first = new AtomicBoolean(true);
        fn.set(req -> {
            if (first.getAndSet(false)) {
                return ChatResponse.builder()
                        .aiMessage(CALL_ME)
                        .tokenUsage(new TokenUsage(9500, 100, 9600))
                        .build();
            }
            return ChatResponse.builder().aiMessage(AiMessage.aiMessage("Okay thats good")).build();
        });

        // WHEN — the model compacts in-loop
        subject.call("Foo", null);

        // THEN — the counter reflects the NEW small memory, not the pre-compact request usage
        assertThat(subject.getMemory().getTotalTokenUsed()).isLessThan(1000);
        // AND — no compact hint was injected right after the compact
        assertThat(subject.getMemory().containsUserMessage("CONTEXT LIMIT WARNING")).isFalse();
    }

    @Test
    void test_inLoopCompact_noHintAfterCompact() {
        // GIVEN — realistic budget (80k) and the slave compact factor (0.7 -> 56k pre-turn trigger)
        var config = LlmConfig.newOllama("foo").toBuilder().autoCompactAfter(80000).build();
        subject = new AiDevAgent(new ConfiguredChatModel(config, cm), toolService, 0.7);
        for (int i = 0; i < 5; i++) {
            subject.addMessage(UserMessage.from("Foo " + i));
            subject.addMessage(AiMessage.from("Bar " + i));
        }
        var first = new AtomicBoolean(true);
        fn.set(req -> {
            if (first.getAndSet(false)) {
                return ChatResponse.builder()
                        .aiMessage(CALL_ME)
                        .tokenUsage(new TokenUsage(76000, 1000, 77000))
                        .build();
            }
            return ChatResponse.builder().aiMessage(AiMessage.aiMessage("Okay thats good")).build();
        });

        // WHEN — turn 1 compacts in-loop; turn 2 must NOT trigger a phantom pre-turn auto-compact
        subject.call("Foo", null);
        subject.call("Bar", null);

        // THEN — no compact hint was injected
        assertThat(subject.getMemory().containsUserMessage("CONTEXT LIMIT WARNING")).isFalse();
        // AND — the compressor ran exactly once (turn 1: request, compact, final; turn 2: final)
        verify(cm, times(4)).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
    }

    @Test
    void test_context_injection() {
        // GIVEN — turn context is restored after compact, not injected on first call
        var requestRef  = new AtomicReference<ChatRequest>();
        fn.set(req -> {
            requestRef.set(req);
            return ChatResponse.builder().aiMessage(AiMessage.aiMessage("Okay thats good")).build();
        });

        // WHEN — set turn context via turnContextSupplier
        subject.setTurnContextSupplier(() -> List.of(
                new SimpleContextItem("We are all doomed!"),
                new ContextItem() {
                    @Override
                    public String dedupKey() { return "foo/path/bar.txt"; }
                    @Override
                    public String render() { return "some nice file text"; }
                },
                new ContextItem() {
                    @Override
                    public String dedupKey() { return "foo/null.txt"; }
                    @Override
                    public String render() { return null; }
                }
            )
        );
        subject.call("Foo 1", null);

        // THEN — on first call, turn context is NOT injected (only restored after compact)
        verify(cm, times(1)).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        // AND — only the explicit user message is in the request
        var userTexts = String.join(
            System.lineSeparator(),
            requestRef.get().messages().stream()
                .map(ChatMessageUtil::toString)
                .toList());

        assertThat(userTexts).contains("Foo 1");
        assertThat(userTexts).doesNotContain("null");
        // AND
        assertThat(userTexts).contains("foo/path/bar.txt");
        assertThat(userTexts).contains("some nice file text");
        // AND
        assertThat(userTexts).doesNotContain("foo/null.txt");
        assertThat(userTexts).doesNotContain("null");

        // AND — turn context survives via turnContextSupplier for compact restore
        assertThat(userTexts).contains("USER: We are all doomed!");
        
        // WHEN
        subject.call("Foo 2", null);
        subject.call("Foo 3", null);
        
        userTexts = String.join(
                System.lineSeparator(),
                requestRef.get().messages().stream()
                    .filter(m -> m instanceof UserMessage)
                    .map(ChatMessageUtil::toString)
                    .toList());
        // THEN
        assertThat(userTexts).contains("Foo 2");
        assertThat(userTexts).contains("Foo 3");
        assertThat(requestRef.get().messages().stream()
                .filter(m -> m instanceof UserMessage)
                .map(ChatMessageUtil::toString)
                .filter(m -> m.contains("some nice file text"))
                .count()).isOne();
    }
    
    @Test
    void test_command_as_standing_order() {
        // GIVEN — a command body set as turn context; compact restores it
        var requestRef = new AtomicReference<ChatRequest>();
        AtomicInteger callCount = new AtomicInteger();
        fn.set(req -> {
            requestRef.set(req);
            callCount.incrementAndGet();
            return ChatResponse.builder()
                    .aiMessage(AiMessage.aiMessage("Review complete — no issues found."))
                    .build();
        });

        // WHEN — set turn context and call
        subject.setTurnContextSupplier(() -> List.of(new SimpleContextItem("Review the code and report any issues.")));
        subject.call("Refactor this class", null);

        // THEN — on first call, turn context is NOT injected (only restored after compact)
        verify(cm, times(1)).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        var userMsg = ChatMessageUtil.toString(subject.getMemory().get(0));
        assertThat(userMsg).contains("Refactor this class");
        // AND — turn context survives via turnContextSupplier for compact restore
        assertThat(ChatMessageUtil.readChatMessage(requestRef.get().messages()))
            .contains("Review the code and report any issues.");
    }

    @Test
    void test_send_no_message() {
        // GIVEN
        var aiMessage = AiMessage.aiMessage("Okay thats good");
        var userMessage = UserMessage.from("Some message");

        var requestRef  = new AtomicReference<ChatRequest>();
        subject.getMemory().add(UserMessage.from("Some message"));
        fn.set(req -> {
            requestRef.set(req);
            return ChatResponse.builder().aiMessage(aiMessage).build();
        });
        
        // WHEN
        subject.call(null, null);
        subject.call(null, null);

        // THEN
        verify(cm, times(2)).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));

        // AND
        var mem = subject.getMemory().getCopy();
        assertThat(mem.size()).isEqualTo(3);
        assertThat(mem.get(0)).isEqualTo(userMessage);
        assertThat(mem.get(1)).isEqualTo(aiMessage);
        assertThat(mem.get(2)).isEqualTo(aiMessage);
    }
    
    private StreamingChatModel mockWithHandler() {
        var cm = mock(StreamingChatModel.class);
        doAnswer(inv -> {
            ChatRequest req = inv.getArgument(0, ChatRequest.class);
            ChatResponse cr = fn.get().apply(req);
            var handler = inv.getArgument(1, StreamingChatResponseHandler.class);
            handler.onCompleteResponse(cr);
            return null;
        }).when(cm).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        return cm;
    }
}
