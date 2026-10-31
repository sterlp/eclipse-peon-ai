package org.sterl.llmpeon.tool;

import java.lang.reflect.Method;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;
import org.sterl.llmpeon.agent.model.TaggedResult;
import org.sterl.llmpeon.compact.HandleToolLoopCompactComponent;
import org.sterl.llmpeon.exception.ExceptionUtil;
import org.sterl.llmpeon.mcp.McpService;
import org.sterl.llmpeon.queuedmessages.QueuedUserMessages;
import org.sterl.llmpeon.shared.OwnerMonitor;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;
import org.sterl.llmpeon.tool.model.ToSimpleMessage;
import org.sterl.llmpeon.tool.tools.AbstractTool;
import org.sterl.llmpeon.tool.tools.CompactSessionTool;
import org.sterl.llmpeon.tool.tools.SearchAgentTool;
import org.sterl.llmpeon.tool.tools.ShellTool;
import org.sterl.llmpeon.tool.web.WebFetchTool;
import org.sterl.llmpeon.tool.web.WebSearchTool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Owns all tools and the tool registry.
 * Tools are stable instances — only the context changes when the user selects a different file/project.
 *
 * <p>ADR-0069: MCP groups are ADOPTED, never owned — {@link #adoptMcpGroup} registers an
 * already-connected {@link McpService} under a group name, and {@code disconnectMcp} only
 * detaches this registry. The owner (the connection service) creates, connects and closes the
 * service; this registry never does, so one service can be adopted into several registries
 * without duplicating or leaking the process.
 *
 * https://github.com/langchain4j/langchain4j/blob/main/docs/docs/tutorials/tools.md
 */
@Slf4j
public class ToolService {

    private static final int MAX_STUCK_ITERATIONS = 10;
    private final Map<String, SmartToolExecutor> toolExecutors = new ConcurrentHashMap<>();

    /** Public: the compact stage-1 reduction references it as a named constant (ADR-0056.4). */
    public static final String COMPACT_HINT =
            "CONTEXT LIMIT WARNING: Call '" + CompactSessionTool.NAME + "' as your first tool call. " +
            "In the 'preserve' field, summarize the critical next steps and any findings needed to continue. " +
            "Include hard-won facts that are expensive to rediscover — exact file:line locations and key decisions with their rationale (the why) — and what to do next.";

    private static final String STUCK_MESSAGE = """
            Your last response contained only internal reasoning with no output.
            Stop thinking and take action now: either call a tool, ask a clarifying question,
            or provide your answer directly.""";

    /** ADR-0068: named MCP groups — the user-config group plus built-ins (e.g. "codegraph").
     *  ADR-0069: public because the connection services adopt into / detach this group by name. */
    public static final String USER_GROUP = "user";
    private final Map<String, McpService> mcpGroups = new ConcurrentHashMap<>();
    private volatile List<ToolSpecification> mcpToolSpecs = List.of();

    public ToolService() {
        this(true);
    }

    /**
     * @param withDefaults if true, auto-registers the always-on tool set: WebFetchTool,
     *        WebSearchTool, SearchAgentTool, ShellTool, CompactSessionTool
     */
    public ToolService(boolean withDefaults) {
        if (withDefaults) {
            addTool(new WebFetchTool());
            addTool(new WebSearchTool());
            addTool(new SearchAgentTool(this));
            addTool(new ShellTool());
            addTool(new CompactSessionTool());
        }
    }

    public List<ToolSpecification> toolSpecifications() {
        return toolExecutors.values().stream()
                .map(SmartToolExecutor::getSpec)
                .toList();
    }

    /** All registered built-in tool executors (UI introspection). */
    public java.util.Collection<SmartToolExecutor> getExecutors() {
        return toolExecutors.values();
    }

    /** Names of the currently connected MCP tools (empty when no MCP server is connected). */
    public List<String> mcpToolNames() {
        return mcpToolSpecs.stream().map(ToolSpecification::name).toList();
    }

    /**
     * Sorted names of all tools (built-in + MCP) active under the given filters — the single
     * preview source (R-ATC-8): classFilter applies to the built-in executors, nameFilter to
     * both built-in and MCP names.
     */
    @SuppressWarnings("null")
    public List<String> activeNames(Predicate<SmartToolExecutor> classFilter,
            Predicate<String> nameFilter) {
        var builtIns = getExecutors().stream()
                .filter(classFilter)
                .map(exec -> exec.getSpec().name())
                .filter(nameFilter);
        var mcp = mcpToolNames().stream().filter(nameFilter);
        return Stream.concat(builtIns, mcp).sorted(String::compareTo).toList();
    }

    List<ToolSpecification> toolSpecifications(ToolLoopRequest req) {
        var result = new ArrayList<ToolSpecification>();
        toolExecutors.values().stream()
                .filter(req.toolFilter)
                .map(SmartToolExecutor::getSpec)
                .forEach(result::add);

        mcpToolSpecs.stream()
                .filter(spec -> req.toolNameFilter.test(spec.name()))
                .forEach(result::add);
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
        // ADR-0078: THE loop stamp — every line of this loop (model answer, tool line, validator
        // denial) carries the owner's name, whoever emits it. No owner means no stamp, never an
        // invented one; a member loop already stamped by its agent keeps that name (fill-if-null).
        var ownerName = req.owner().name();
        if (ownerName != null) req.monitor(new OwnerMonitor(ownerName, req.getMonitor()));

        ChatResponse response = null;

        int iterations = 0;
        boolean shouldLoop = true;
        int stuck = 0;
        // R-CC-18: the compact decision of THIS loop — the component is stateless (R-CC-18.5), a
        // failed direct compact ends the turn instead of blocking later rounds (ADR-0070)
        var compactHandler = new HandleToolLoopCompactComponent();

        do {
            var messages = new ArrayList<ChatMessage>(toOneSystemMessage(req.staticMessages));
            req.getMemory().addMemoryTo(messages);

            var agentConfig = req.getAgentConfig();
            var specs = toolSpecifications(req);
            // R-CC-15: the honest fallback binds to "this tool set does not offer compactSession"
            // — a non-empty set WITHOUT it must take the fallback, not the hint
            var hasCompactTool = specs.stream()
                    .anyMatch(spec -> CompactSessionTool.NAME.equals(spec.name()));
            var builder = ChatRequest.builder()
                    .messages(messages)
                    .parameters(agentConfig.newRequestParameters(specs));

            req.getMonitor().onChatMessage(++iterations, builder);
            response = req.call(builder.build());

            if (response == null) {
                throw new IllegalStateException("AI call returned null — streaming failed without a response");
            }

            ToSimpleMessage.INSTANCE.convert(response.aiMessage()).forEach(req.monitor::onChatResponse);

            if (req.getMonitor().isCanceled()) break; // TODO re-think this

            var isToolrequest = response.aiMessage().hasToolExecutionRequests();
            var hasResponseMessage = StringUtil.hasValue(response.aiMessage().text());
            var hasThink = StringUtil.hasValue(response.aiMessage().thinking());
            shouldLoop = isToolrequest || (hasThink && !hasResponseMessage);

            // we always add the AI messages later
            // 1. we may cancel and have tool request with no response -> error in next cycle
            // 2. tools may clear the memory
            if (isToolrequest) {
                stuck = 0; // reset on productive tool use
                var tR = runAllTools(response, req);
                req.getMemory().addResult(response, tR);
                // R-CC-18: the state-based decision runs after EVERY tool round — over budget the
                // component compacts directly, in the band it hints (one implementation, ADR-0070)
                compactHandler.handle(req, hasCompactTool);

            } else if (hasResponseMessage) {
                stuck = 0; // reset on productive response
                req.getMemory().addResult(response);
                break; // done
            } else if (hasThink) {
                // https://github.com/langchain4j/langchain4j/issues/4786
                ++stuck;
                // we cannot just add the message in this case as is
                // -> "Assistant message must contain either 'content' or 'tool_calls'!","type":"invalid_request_error"
                req.getMemory().add(AiMessage.from(response.aiMessage().thinking()));
                req.monitor.onProblem("AI hangs - only thinking returned times: " + stuck);
                if (stuck > MAX_STUCK_ITERATIONS) break;
                if (stuck > MAX_STUCK_ITERATIONS - 2) compactHandler.forceHint(req, hasCompactTool); // R-CC-18: force bypasses off-switch + band
                else req.addMessage(new UserMessage(STUCK_MESSAGE));
            }
        } while (shouldLoop && !req.monitor.isCanceled());

        return response;
    }

    private List<ToolExecutionResultMessage> runAllTools(ChatResponse response, ToolLoopRequest req) {
        var toolResults = new ArrayList<ToolExecutionResultMessage>();

        for (var tr : response.aiMessage().toolExecutionRequests()) {
            try {
                var trResult = execute(tr, req);
                toolResults.add(trResult);
            } catch (Exception e) {
                if (ExceptionUtil.isCanceled(e)) throw e;
                log.error("Tool {} with args {} failed", tr.name(), tr.arguments(), e);
                req.getMonitor().onProblem(StringUtil.toolFailureLine(tr.name(), e, Clock.systemDefaultZone()));
                toolResults.add(ToolExecutionResultMessage.from(tr.id(), tr.name(),
                        "Tool failed - check why and inform user " + StringUtil.getStackTrace(e)));
            }
        }
        attachUrgentMessages(toolResults, req);

        return toolResults;
    }

    /**
     * Rule 11: the urgent entries a round picked up ride on <b>one</b> result, so several tool calls
     * of one answer still deliver the block exactly once. The target is the first result not already
     * carrying a block — a result that does one ends with the user's answer (R12) and must keep it as
     * the last line. The block goes behind the tool's own output, which keeps the result a non-pure
     * block (R-TR-2): the compact keeps it. An agent-less loop (a search sub-agent) has no queue of
     * its own — its parent must still see the entries, so nothing is drained there.
     */
    private void attachUrgentMessages(List<ToolExecutionResultMessage> toolResults, ToolLoopRequest req) {
        var agent = req.getAgent();
        if (agent == null || toolResults.isEmpty()) return;

        // the target is picked BEFORE the drain — a drain is final and discloses, so entries without
        // a consumer would be lost silently; they stay queued for the follow-up turn instead (R8.3)
        var target = -1;
        for (int i = 0; i < toolResults.size(); i++) {
            if (!QueuedUserMessages.carriesBlock(toolResults.get(i).text())) { target = i; break; }
        }
        if (target < 0) return;

        var block = QueuedUserMessages.block(agent.takeUrgentMessages(req.getMonitor()));
        if (block == null) return;

        var at = toolResults.get(target);
        toolResults.set(target, ToolExecutionResultMessage.from(at.id(), at.toolName(),
                at.text() + TaggedResult.LN + TaggedResult.LN + block));
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

    /**
     * Runs one tool call and builds the result the model reads.
     */
    public ToolExecutionResultMessage execute(ToolExecutionRequest tr, ToolLoopRequest req) {
        var executor = toolExecutors.get(tr.name());
        var monitor = req.getMonitor();
        String result;
        var mcpGroupService = executor == null ? findMcpService(tr.name()) : null;
        var isMcpTool = mcpGroupService != null;
        if (isMcpTool) {
            monitor.onTool("Running MCP: " + tr.name() + " - " + tr.arguments());
            result = mcpGroupService.executeTool(tr);
            monitor.onTool("MCP " + tr.name() + " done. " + StringUtil.charsSuffix(result));
            log.debug("MCP Tool {} result size: {}", tr.name(), result == null ? "null" : result.length());
            // R-RS-3: a null result is an honest empty for the LLM (langchain4j rejects null message text)
            return ToolExecutionResultMessage.from(tr, result == null ? "" : result);
        } else if (executor == null) {
            result = "Error: unknown tool '" + tr.name() + "' check spelling";
            monitor.onProblem(result);
        } else {
            result = executor.run(tr, req);
        }
        return ToolExecutionResultMessage.from(tr, result);
    }

    /**
     * ADR-0069: registers an already-connected McpService under the given group name.
     * The registry ADOPTS the connection — it never closes it. disconnectMcp(group) only
     * detaches this registry; the owner (connection service) closes the service.
     * Re-adopting a group name replaces the previous entry WITHOUT closing it.
     */
    public void adoptMcpGroup(String group, McpService service) {
        mcpGroups.put(group, service);
        refreshMcpSpecs();
    }

    /** Rebuilds the tool-spec union across all groups — deterministic: group names sorted, then spec order. */
    private void refreshMcpSpecs() {
        var union = new ArrayList<ToolSpecification>();
        mcpGroups.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> union.addAll(entry.getValue().getToolSpecifications()));
        this.mcpToolSpecs = union;
    }

    /** First group (sorted by name) that owns the tool name — tool names are unique across groups. */
    private McpService findMcpService(String toolName) {
        return mcpGroups.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .filter(service -> service.hasTool(toolName))
                .findFirst()
                .orElse(null);
    }

    /** Detaches the user-group MCP connection from this registry (never closes the service — ADR-0069). */
    public void disconnectMcp() {
        disconnectMcp(USER_GROUP);
    }

    /** Detaches the named group (map entry + spec union). NEVER closes the McpService (ADR-0069). */
    public void disconnectMcp(String group) {
        if (mcpGroups.remove(group) != null) refreshMcpSpecs();
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