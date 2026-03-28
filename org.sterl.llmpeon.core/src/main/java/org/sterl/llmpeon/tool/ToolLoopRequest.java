package org.sterl.llmpeon.tool;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Command object for {@link ToolService#executeLoop(ToolLoopRequest)}.
 * Required fields: {@code memory} and {@code chatModel}.
 * All other fields have sensible defaults.
 */
public class ToolLoopRequest {

    // required
    public final ChatMemory memory;
    public final ChatModel chatModel;

    // optional — with defaults
    public List<ChatMessage> staticMessages = List.of();
    public AiMonitor monitor = AiMonitor.NULL_MONITOR;
    public Predicate<SmartToolExecutor> toolFilter = t -> true;
    public boolean includeMcpTools = true;
    public double temperature = 0.8;
    public Consumer<ChatResponse> onLoop = r -> {};

    public ToolLoopRequest(ChatMemory memory, ChatModel chatModel) {
        this.memory = memory;
        this.chatModel = chatModel;
    }

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
