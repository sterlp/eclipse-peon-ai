package org.sterl.llmpeon.tool;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.jspecify.annotations.NonNull;
import org.sterl.llmpeon.mcp.McpServerConfig;
import org.sterl.llmpeon.mcp.McpService;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;
import org.sterl.llmpeon.tool.model.ToSimpleMessage;
import org.sterl.llmpeon.tool.tools.AbstractTool;
import org.sterl.llmpeon.tool.tools.SearchAgentTool;
import org.sterl.llmpeon.tool.tools.ShellTool;
import org.sterl.llmpeon.tool.tools.WebFetchTool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Owns all tools and the tool registry.
 * Tools are stable instances — only the context changes when the user selects a different file/project.
 *
 * https://github.com/langchain4j/langchain4j/blob/main/docs/docs/tutorials/tools.md
 */
@Slf4j
public class ToolService {

    public static final int MAX_ITERATIONS = 75;

    private final Map<String, SmartToolExecutor> toolExecutors = new ConcurrentHashMap<>();

    private McpService mcpService;
    private List<ToolSpecification> mcpToolSpecs = List.of();

    public ToolService() {
        super();
        addTool(new WebFetchTool());
        addTool(new SearchAgentTool(this));
        addTool(new ShellTool());
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
     * Runs the full tool loop: calls the model via streaming, executes any tools, repeats until
     * the model produces a plain text response.
     * 
     * TODO: https://github.com/sterlp/eclipse-peon-ai/issues/55
     * Keep in mind any change to the message history may kill the kv cache!!
     * https://github.com/sterlp/eclipse-peon-ai/issues/60
     */
    @NonNull
    public ChatResponse executeLoop(@NonNull ToolLoopRequest req) {
        ChatResponse response = null;

        int iterations = 0;
        boolean shouldLoop = true;
        do {
            var messages = new ArrayList<ChatMessage>(req.staticMessages);
            messages.addAll(req.memory.messages());
            messages.addAll(req.userContextInformations); // keep it close to the user message to have the cache working
            if (req.userMessage != null) messages.add(req.userMessage);

            var builder = ChatRequest.builder()
                    .temperature(req.temperature)
                    .messages(toOneSystemMessage(messages));

            var toolSpecs = new ArrayList<>(toolSpecifications(req.toolFilter));
            if (req.includeMcpTools) toolSpecs.addAll(mcpToolSpecs);
            if (!toolSpecs.isEmpty()) {
                builder.toolSpecifications(toolSpecs);
            }

            req.monitor.onChatMessage(iterations + 1, builder);

            response = req.bridge.call(req.chatModel, builder.build(), req.monitor);

            shouldLoop = response.aiMessage().hasToolExecutionRequests()
                    || (StringUtil.hasNoValue(response.aiMessage().text())
                            && StringUtil.hasValue(response.aiMessage().thinking())
                        );
            if (shouldLoop) req.onLoop.accept(response);

            ToSimpleMessage.INSTANCE.convert(response.aiMessage()).forEach(req.monitor::onChatResponse);

            req.memory.set(runAllTools(response, req.chatModel, req.monitor, req.memory.messages()));


            // https://github.com/langchain4j/langchain4j/issues/4786
            if (StringUtil.hasNoValue(response.aiMessage().text())
                            && StringUtil.hasValue(response.aiMessage().thinking())) {
                if (!response.aiMessage().hasToolExecutionRequests()) {
                    req.memory.add(new UserMessage("Continue based on your thinking:\n"
                            + response.aiMessage().thinking()));
                }
            }

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

    public List<ChatMessage> runAllTools(ChatResponse response, StreamingChatModel agentService, AiMonitor monitor, List<ChatMessage> memory) {
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

    public static record ToolResult(boolean clearMemory, ToolExecutionResultMessage message) {}

    public ToolResult execute(ToolExecutionRequest tr, AiMonitor monitor, StreamingChatModel agentService, List<ChatMessage> memory) {
        var executor = toolExecutors.get(tr.name());
        monitor = AiMonitor.nullSafety(monitor);
        String result;
        if (executor == null && mcpService != null && mcpService.hasTool(tr.name())) {
            monitor.onTool("Running MCP: " + tr.name() + " - " + tr.arguments());
            result = mcpService.executeTool(tr);
            log.debug("Tool {}:\n{}", tr.name(), result);
            return new ToolResult(false, ToolExecutionResultMessage.from(tr.id(), tr.name(), result));
        } else if (executor == null) {
            result = "Error: unknown tool '" + tr.name() + "' check spelling";
            monitor.onProblem(result);
        } else {
            result = executor.run(tr, monitor, agentService, memory);
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
        var old = replaceTool(toolObject);
        if (old != null) throw new RuntimeException("Tool " + old.getSpec().name() + " already registered ...");
    }
    
    public SmartToolExecutor replaceTool(SmartTool toolObject) {
        SmartToolExecutor result = null;
        for (Method method : toolObject.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                var spec = ToolSpecifications.toolSpecificationFrom(method);
                var old = toolExecutors.put(spec.name(), new SmartToolExecutor(toolObject, method, spec));
                if (old != null) {
                    result = old;
                    log.info("replaced tool " + spec);
                } else {
                    log.debug("added tool   " + spec);
                }
            }
        }
        return result;
    }

    /** Removes all tools registered from the given tool object. */
    public void removeTool(SmartTool toolObject) {
        removeTool(toolObject.getClass());
    }
    
    /** Removes all tools registered from the given tool object. */
    public void removeTool(Class<? extends SmartTool> toolClass) {
        for (Method method : toolClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                var spec = ToolSpecifications.toolSpecificationFrom(method);
                toolExecutors.remove(spec.name());
                log.debug("removed tool " + spec.name());
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
