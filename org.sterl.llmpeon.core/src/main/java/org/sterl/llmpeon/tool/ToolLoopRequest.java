package org.sterl.llmpeon.tool;

import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.streaming.StreamingBridge;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
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
    private final ThreadSafeMemory memory;
    @Getter
    @NonNull
    private final ConfiguredChatModel chatModel;
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
    /**
     * Filters tools by name — applied to MCP tool specs (which are not covered by
     * {@link #toolFilter}). Lets a custom agent's allowlist govern MCP tools too. Default: allow all.
     */
    @Default
    public Predicate<String> toolNameFilter = n -> true;
    @Nullable
    public Double temperature;
    /** Per-agent model override — null means use the configured default. */
    @Nullable
    public String modelName;

    /**
     * Standing orders (project context, AGENTS.md, active command/skill body) captured at loop
     * start. Re-injected as user messages by {@link #clearMemory()} so they survive a
     * {@code compactSession} tool call mid-loop.
     */
    @Default
    @Getter
    public List<String> standingOrders = List.of();

    public void addMessage(ChatMessage message) {
        memory.add(message);
    }

    /**
     * Clears the conversation memory and re-injects the standing orders as user messages, so a
     * command/skill keeps governing the task after a compaction. With no standing orders this
     * behaves like {@code memory.clear()}.
     */
    public void clearMemory() {
        memory.clear();
        if (standingOrders == null) return;
        for (var order : standingOrders) {
            memory.add(UserMessage.from(order));
        }
    }

    public LlmConfig getConfig() {
        return chatModel.getConfig();
    }
    
    public ChatResponse call(ChatRequest chatRequest) {
        return bridge.call(chatModel.getChatModel(), chatRequest, monitor);
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

    public ToolLoopRequest temperature(double temperature) {
        this.temperature = temperature;
        return this;
    }

    public ToolLoopRequest modelName(String modelName) {
        this.modelName = modelName;
        return this;
    }
}
