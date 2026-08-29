package org.sterl.llmpeon.tool;

import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.ai.AgentConfig;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.streaming.ApiRetry;
import org.sterl.llmpeon.streaming.StreamingBridge;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;

import dev.langchain4j.data.message.ChatMessage;
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
    /** Earned-patience retry around the AI call; fresh per turn (this object is rebuilt per message). */
    @Default
    private final ApiRetry retry = new ApiRetry();

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
    /**
     * Per-agent write-path validator. Set by every agent via {@code AiAgent.getWriteValidator()}.
     * Default: allow all — only Peon-PO (Jon) restricts it.
     */
    @Default
    @Getter
    public WriteValidator writeValidator = WriteValidator.ALLOW_ALL;
    /**
     * Per-agent config (provider, model, think, temperature). Set by every agent via
     * {@link org.sterl.llmpeon.agent.AbstractAgent#getConfig()}. When unset (e.g. a bare
     * builder in a test), {@link #getAgentConfig()} falls back to the dev config.
     */
    @Nullable
    public AgentConfig agentConfig;

    /**
     * Owning agent for this tool loop. Set by {@link org.sterl.llmpeon.agent.AbstractAgent#doCall(String, AiMonitor)}
     * so tools can delegate back to the agent (e.g. {@code compactSession}).
     */
    @Nullable
    @Getter
    public AiAgent agent;

    public void addMessage(ChatMessage message) {
        memory.add(message);
    }

    public LlmConfig getConfig() {
        return chatModel.getConfig();
    }

    /** Per-agent config for this loop; falls back to the dev config when not explicitly set. */
    public AgentConfig getAgentConfig() {
        return agentConfig != null ? agentConfig : chatModel.getConfig().devAgentConfig();
    }
    
    public ChatResponse call(ChatRequest chatRequest) {
        return retry.call(monitor, () -> bridge.call(chatModel.modelFor(getAgentConfig()), chatRequest, monitor));
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

    public ToolLoopRequest agentConfig(AgentConfig agentConfig) {
        this.agentConfig = agentConfig;
        return this;
    }

    public ToolLoopRequest agent(AiAgent agent) {
        this.agent = agent;
        return this;
    }
}
