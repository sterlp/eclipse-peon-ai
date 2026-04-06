package org.sterl.llmpeon.tool;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.jspecify.annotations.NonNull;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;
import org.sterl.llmpeon.tool.model.ToSimpleMessage;
import org.sterl.llmpeon.tool.tools.AbstractTool;
import org.sterl.llmpeon.tool.tools.CompressorAgentTool;
import org.sterl.llmpeon.tool.tools.SearchAgentTool;
import org.sterl.llmpeon.tool.tools.ShellTool;
import org.sterl.llmpeon.tool.tools.WebFetchTool;

import org.sterl.llmpeon.mcp.McpServerConfig;
import org.sterl.llmpeon.mcp.McpService;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Owns all tools and the tool registry.
 * Tools are stable instances — only the context changes when the user selects a different file/project.
 * 
 * https://github.com/langchain4j/langchain4j/blob/main/docs/docs/tutorials/tools.md
 */
public class ToolService {

    public static final int MAX_ITERATIONS = 75;

    private final Map<String, SmartToolExecutor> toolExecutors = new HashMap<>();

    private McpService mcpService;
    private List<ToolSpecification> mcpToolSpecs = List.of();

    public ToolService() {
        super();
        addTool(new WebFetchTool());
        addTool(new SearchAgentTool(this));
        addTool(new ShellTool());
        addTool(new CompressorAgentTool());
    }

    public List<ToolSpecification> toolSpecifications() {
        return toolExecutors.values().stream()
                .map(SmartToolExecutor::getSpec)
                .toList();
    }
    
    public List<ToolSpecification> toolSpecifications(Predicate<SmartToolExecutor> filter) {
        return toolExecutors.values().stream()
                .filter(filter)
                .map(SmartToolExecutor::getSpec)
                .toList();
    }

    public SmartToolExecutor getExecutor(String toolName) {
        return toolExecutors.get(toolName);
    }

    /**
     * Runs the full tool loop: calls the model, executes any tools, repeats until
     * the model produces a plain text response.
     */
    @NonNull
    public ChatResponse executeLoop(@NonNull ToolLoopRequest req) {
        ChatResponse response = null;

        int iterations = 0;
        boolean shouldLoop = true;
        do {
            var messages = new ArrayList<ChatMessage>(req.staticMessages);
            messages.addAll(req.memory.messages());

            // presencePenalty not
            var builder = ChatRequest.builder()
                    .temperature(req.temperature)
                    .messages(toOneSystemMessage(messages));

            var toolSpecs = new ArrayList<>(toolSpecifications(req.toolFilter));
            if (req.includeMcpTools) toolSpecs.addAll(mcpToolSpecs);
            if (!toolSpecs.isEmpty()) builder.toolSpecifications(toolSpecs);

            req.monitor.onChatMessage(iterations + 1, builder);

            response = req.chatModel.chat(builder.build());

            shouldLoop = response.aiMessage().hasToolExecutionRequests()
                    // https://github.com/langchain4j/langchain4j/issues/4786
                    || (StringUtil.hasNoValue(response.aiMessage().text())
                            && StringUtil.hasValue(response.aiMessage().thinking())
                        );
            if (shouldLoop) req.onLoop.accept(response);

            ToSimpleMessage.INSTANCE.convert(response.aiMessage()).forEach(req.monitor::onChatResponse);

            req.memory.set(runAllTools(response, req.chatModel, req.monitor, req.memory.messages()));

            if (iterations++ >= MAX_ITERATIONS) {
                req.monitor.onProblem("Tool loop reached max iterations - stopping after " + iterations);
                break;
            }
            if (req.monitor.isCanceled()) break;
        } while (shouldLoop);

        return response;
    }

    /** Merges all SystemMessages into one at the front (compatibility with local LLMs). */
    private static List<ChatMessage> toOneSystemMessage(List<ChatMessage> messages) {
        var result = new ArrayList<ChatMessage>();
        var systemText = new StringBuilder();
        for (var m : messages) {
            if (m instanceof SystemMessage sm) systemText.append(sm.text()).append("\n");
            else result.add(m);
        }
        if (systemText.length() > 0) result.addFirst(SystemMessage.from(systemText.toString()));
        return result;
    }
    
    public List<ChatMessage> runAllTools(ChatResponse response, ChatModel agentService, AiMonitor monitor, List<ChatMessage> memory) {
        if (!response.aiMessage().hasToolExecutionRequests()) {
            // No tools to run — still add the final AI message to memory
            var newMemory = new ArrayList<ChatMessage>(memory);
            newMemory.add(response.aiMessage());
            return newMemory;
        }

        var toolResults = new ArrayList<ToolExecutionResultMessage>();
        boolean clearMemory = false;

        for (var tr : response.aiMessage().toolExecutionRequests()) {
            var trResult = execute(tr, monitor, agentService, memory);
            toolResults.add(trResult.message());
            if (trResult.clearMemory()) clearMemory = true;
            if (monitor.isCanceled()) break;
        }

        var newMemory = new ArrayList<ChatMessage>();
        if (!clearMemory) newMemory.addAll(memory);
        newMemory.add(response.aiMessage());
        newMemory.addAll(toolResults);
        return newMemory;
    }

    static record ToolResult(boolean clearMemory, ToolExecutionResultMessage message) {}
    
    public ToolResult execute(ToolExecutionRequest tr, AiMonitor monitor, ChatModel agentService, List<ChatMessage> memory) {
        var executor = toolExecutors.get(tr.name());
        String result;
        if (executor == null && mcpService != null && mcpService.hasTool(tr.name())) {
            monitor.onTool("Running MCP tool " + tr.name());
            result = mcpService.executeTool(tr);
            return new ToolResult(false, ToolExecutionResultMessage.from(tr.id(), tr.name(), result));
        } else if (executor == null) {
            result = "Error: unknown tool '" + tr.name() + "' check spelling";
            AiMonitor.nullSafety(monitor).onProblem(result);
        } else {
            try {
                result = executor.run(tr, monitor, agentService, memory);
            } catch (IllegalArgumentException e) {
                // user-facing argument error — return as-is so the LLM can correct itself
                result = e.getMessage();
                AiMonitor.nullSafety(monitor).onProblem(tr.name() + ": " + result);
            }
        }
        return new ToolResult(
                executor == null ? false : executor.shouldClearMemory(), 
                ToolExecutionResultMessage.from(tr.id(), tr.name(), result));
    }
    
    /**
     * Connects to all given MCP servers and makes their tools available in the tool loop.
     * Disconnects any previously active MCP connection first.
     * Throws if any server fails to connect.
     */
    public void connectMcp(List<McpServerConfig> servers) {
        disconnectMcp();
        if (servers == null || servers.isEmpty()) return;
        var service = new McpService(servers);
        service.connect(); // throws on failure
        this.mcpService = service;
        this.mcpToolSpecs = service.getToolSpecifications();
    }

    /** Disconnects the active MCP service and removes its tools from the tool loop. */
    public void disconnectMcp() {
        this.mcpToolSpecs = List.of();
        if (mcpService != null) {
            mcpService.disconnect();
            mcpService = null;
        }
    }

    /**
     * Registers any object that has methods annotated with {@link Tool}.
     * Existing tools with the same name trigger an error.
     */
    public void addTool(SmartTool toolObject) {
        for (Method method : toolObject.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                var spec = ToolSpecifications.toolSpecificationFrom(method);
                var old = toolExecutors.put(spec.name(), new SmartToolExecutor(toolObject, method, spec));
                if (old != null) throw new RuntimeException("Tool with " + spec.name() + " already registered ...");
                System.out.println("added tool " + spec);
            }
        }
    }

    /** Removes all tools registered from the given tool object. */
    public void removeTool(SmartTool toolObject) {
        for (Method method : toolObject.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                var spec = ToolSpecifications.toolSpecificationFrom(method);
                toolExecutors.remove(spec.name());
                System.out.println("removed tool " + spec.name());
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    public <T extends AbstractTool> Optional<T> getTool(Class<T> clazz) {
        return toolExecutors.values().stream()
            .map(SmartToolExecutor::getTool)
            .filter(t -> t != null && clazz.isInstance(t))
            .map(t -> (T)t)
            .findFirst();
    }
}
