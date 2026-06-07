package org.sterl.llmpeon.tool;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.ai.ConfiguredModel;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.streaming.StreamingBridge;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NonNull;

/**
 * Command object for {@link ToolService#executeLoop(ToolLoopRequest)}.
 * Required fields: {@code memory} and {@code model}. The {@code bridge} has a default value.
 * All other fields have sensible defaults.
 * 
 * Keep in mind any change to the message history may kill the kv cache!!
 * https://github.com/sterlp/eclipse-peon-ai/issues/60
 * 
 * See also:
 * https://github.com/ggml-org/llama.cpp/issues/22746
 * https://github.com/ggml-org/llama.cpp/pull/13194#issuecomment-4586088278
 */
@Builder(toBuilder = true)
public class ToolLoopRequest {

    @Getter
    @NonNull
    private final ChatMemory memory;
    @Getter
    @NonNull
    private final ConfiguredModel model;
    @Default
    private final StreamingBridge bridge = new StreamingBridge();

    /** static messages which do not change */
    @Default
    public List<ChatMessage> staticMessages = List.of();
    @Default
    @Getter
    public AiMonitor monitor = AiMonitor.NULL_MONITOR;
    @Default
    public Predicate<SmartToolExecutor> toolFilter = t -> true;
    @Default
    public boolean includeMcpTools = true;
    @Nullable
    public Double temperature;
    @Default
    public Consumer<ChatResponse> onLoop = r -> {};

    public void addMessage(ChatMessage message) {
        ChatMessageUtil.addMessageToMemory(memory, message);
    }

    public StreamingChatModel getChatModel() {
        return model.getChatModel();
    }
    
    public ChatResponse call(ChatRequest chatRequest) {
        return bridge.call(model.getChatModel(), chatRequest, monitor);
    }

    /**
     * Pure static messages which never change
     * Keep in mind any change to the message history may kill the kv cache!! 
     */
    public ToolLoopRequest staticMessages(List<ChatMessage> staticMessages) {
        this.staticMessages = staticMessages;
        return this;
    }

    public ToolLoopRequest monitor(AiMonitor monitor) {
        this.monitor = AiMonitor.nullSafety(monitor);
        return this;
    }

    public ToolLoopRequest toolFilter(Predicate<SmartToolExecutor> toolFilter) {
        this.toolFilter = toolFilter;
        return this;
    }

    public ToolLoopRequest includeMcpTools(boolean includeMcpTools) {
        this.includeMcpTools = includeMcpTools;
        return this;
    }

    public ToolLoopRequest temperature(double temperature) {
        this.temperature = temperature;
        return this;
    }

    public ToolLoopRequest onLoop(Consumer<ChatResponse> onLoop) {
        this.onLoop = onLoop;
        return this;
    }
}
