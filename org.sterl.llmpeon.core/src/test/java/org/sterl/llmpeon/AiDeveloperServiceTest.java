package org.sterl.llmpeon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.ConfiguredModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.tool.SmartTool;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.tools.CompactSessionTool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

public class AiDeveloperServiceTest {

    final ToolService toolService = new ToolService();
    AiDeveloperService subject;
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
        subject = new AiDeveloperService(new ConfiguredModel(LlmConfig.newOllama("foo"), cm), toolService);
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
        var mem = subject.getMessages();
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
        // AND
        var mem = subject.getMessages();
        // many models require the first message being a user message
        assertThat(((UserMessage)mem.get(0)).singleText()).contains("Session compacted");
        assertThat(mem.get(1)).isEqualTo(CALL_ME);
        assertThat(mem.get(2)).isInstanceOf(ToolExecutionResultMessage.class);
        assertThat(((ToolExecutionResultMessage)mem.get(2)).text()).contains("Okay thats good");
    }
    
    @Test
    void test_context() {
        // GIVEN
        var requestRef  = new AtomicReference<ChatRequest>();
        subject.addMessage(UserMessage.from("wissen wir schon"));
        fn.set(req -> {
            requestRef.set(req);
            return ChatResponse.builder().aiMessage(AiMessage.aiMessage("Okay thats good")).build();
        });
        
        // WHEN
        subject.setUserContextInformations(Arrays.asList("We are all doomed!", "wissen wir schon"));
        subject.call("Foo", null);

        // THEN
        verify(cm, times(1)).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        // AND
        var d = requestRef.get().messages().stream().map(ChatMessageUtil::toString).filter(m -> m.contains("We are all doomed!")).count();
        assertThat(d).isOne();
        // AND
        d = requestRef.get().messages().stream().map(ChatMessageUtil::toString).filter(m -> m.contains("wissen wir schon")).count();
        assertThat(d).isOne();
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
