package org.sterl.llmpeon.tool;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final int MAX_STUCK_ITERATIONS = 10;
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

    List<ToolSpecification> toolSpecifications(ToolLoopRequest req) {
        var result = new ArrayList<ToolSpecification>();
        toolExecutors.values().stream()
                .filter(req.toolFilter)
                .map(SmartToolExecutor::getSpec)
                .forEach(result::add);
        
        if (req.includeMcpTools) result.addAll(mcpToolSpecs);
        return result;
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
        int stuck = 0;

        do {
            var messages = new ArrayList<ChatMessage>(toOneSystemMessage(req.staticMessages));
            messages.addAll(req.memory.messages());

            var builder = ChatRequest.builder()
                    .messages(messages)
                    .toolSpecifications(toolSpecifications(req));
            if (req.temperature != null) builder.temperature(req.temperature);

            req.monitor.onChatMessage(++iterations, builder);
            response = req.bridge.call(req.chatModel, builder.build(), req.monitor);
            ToSimpleMessage.INSTANCE.convert(response.aiMessage()).forEach(req.monitor::onChatResponse);
            if (req.monitor.isCanceled()) break;

            var isToolrequest = response.aiMessage().hasToolExecutionRequests();
            var hasResponseMessage = StringUtil.hasValue(response.aiMessage().text());
            var hasThink = StringUtil.hasValue(response.aiMessage().thinking());
            
            shouldLoop = isToolrequest || (hasThink && !hasResponseMessage);
            if (shouldLoop) req.onLoop.accept(response);

            if (isToolrequest) {
                stuck = 0; // reset on productive tool use
                var tR = runAllTools(response, req.chatModel, req.monitor, req.memory.messages());
                if (tR.clearMemory()) {
                    req.memory.clear();
                    req.memory.add(UserMessage.from("Session was cleared continue if you have enough informations."));
                }
                // it is possible that the tool fails - as so we have than no tool result, 
                // which will cause issues with some models
                // TODO should we add an info for the AI?
                req.memory.add(response.aiMessage());
                tR.result().forEach(req.memory::add);
            } else if (hasResponseMessage) {
                req.memory.add(response.aiMessage());
                break; // done
            } else if (hasThink) {
                // https://github.com/langchain4j/langchain4j/issues/4786
                ++stuck;
                req.memory.add(response.aiMessage());
                req.monitor.onProblem("AI hangs - only thinking returned times: " + stuck);
                if (stuck > MAX_STUCK_ITERATIONS) break;
                req.memory.add(new UserMessage("Your last response contained only internal reasoning with no output. " +
                                "Stop thinking and take action now: either call a tool, ask a clarifying question, " +
                                "or provide your answer directly."
                            ));
            }
        } while (shouldLoop && !req.monitor.isCanceled());

        return response;
    }

    record ToolLoopResult(ArrayList<ToolExecutionResultMessage> result, boolean clearMemory) {}

    private ToolLoopResult runAllTools(ChatResponse response, StreamingChatModel agentService, AiMonitor monitor, List<ChatMessage> memory) {
        var toolResults = new ArrayList<ToolExecutionResultMessage>();
        boolean clearMemory = false;

        for (var tr : response.aiMessage().toolExecutionRequests()) {
            var trResult = execute(tr, monitor, agentService, memory);
            toolResults.add(trResult.message());
            if (trResult.clearMemory()) clearMemory = true;
            if (monitor.isCanceled()) break;
        }

        return new ToolLoopResult(toolResults, clearMemory);
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
