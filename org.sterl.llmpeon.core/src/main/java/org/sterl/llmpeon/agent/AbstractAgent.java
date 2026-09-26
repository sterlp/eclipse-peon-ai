package org.sterl.llmpeon.agent;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.sterl.llmpeon.ai.AgentConfig;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.compact.CompactConstants;
import org.sterl.llmpeon.compact.CompactService;
import org.sterl.llmpeon.model.CompactResult;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.queuedmessages.UserMessageQueue;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractAgent implements AiAgent {

    @Getter
    protected final ThreadSafeMemory memory;
    protected final ConfiguredChatModel configuredModel;

    protected final ToolService toolService;

    private UserMessageQueue messageQueue = new UserMessageQueue();
    private final AtomicBoolean working = new AtomicBoolean(false);

    private volatile String systemMessage = null;
    /**
     * Build only once
     */
    private List<ContextItem> staticContext;
    private Supplier<List<ContextItem>> turnContextSupplier;

    /**
     * Fraction of the shared auto-compact budget at which THIS agent compacts (1.0 = the full
     * {@link org.sterl.llmpeon.ai.LlmConfig#getAutoCompactAfter() budget}). Jon's RAM-only slaves set it
     * below 1.0 so they compact earlier and keep their throw-away context lean.
     */
    private final double compactFactor;

    protected AbstractAgent(ConfiguredChatModel configuredModel, ToolService toolService) {
        this(configuredModel, toolService, new ThreadSafeMemory());
    }

    protected AbstractAgent(ConfiguredChatModel configuredModel, ToolService toolService, ThreadSafeMemory memory) {
        this(configuredModel, toolService, memory, 1.0);
    }

    protected AbstractAgent(ConfiguredChatModel configuredModel, ToolService toolService, ThreadSafeMemory memory,
            double compactFactor) {
        this.toolService = toolService;
        this.configuredModel = configuredModel;
        this.memory = Objects.requireNonNull(memory, "ThreadSafeMemory cannot be null");
        // Clamp to (0,1]; a nonsense factor falls back to the full budget rather than compacting forever.
        this.compactFactor = compactFactor > 0 && compactFactor <= 1.0 ? compactFactor : 1.0;

        Objects.requireNonNull(this.configuredModel, "ConfiguredChatModel cannot be null");
        Objects.requireNonNull(this.toolService, "ToolService cannot be null");
    }

    /**
     * Derives the per-agent history file from the injected <b>state directory</b> — the directory
     * that contains the {@code <agent>-history.jsonl} files directly (no {@code state} segment).
     * Callers decide where the state lives (ADR-0041 R2: workspace metadata for the plugin,
     * {@link org.sterl.llmpeon.ai.LlmConfig#stateDirectory()} for headless).
     */
    protected static Path historyFile(Path stateDir, String agentName) {
        return stateDir.resolve(safeAgentName(agentName) + "-history.jsonl");
    }

    private static String safeAgentName(String agentName) {
        if (agentName == null || agentName.isBlank()) return "_agent";
        var safe = agentName.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.isBlank() ? "_agent" : safe;
    }

    /**
     * Per-agent {@link AgentConfig} used for every request of this agent (provider, model, think,
     * temperature). Default is the dev/base config; agents override to pick their model+think.
     */
    public AgentConfig getConfig() {
        return configuredModel.getConfig().devAgentConfig();
    }

    /** Dev/default model thinking support. Plan/Custom override. */
    @Override
    public boolean isThinkSupported() {
        return configuredModel.getConfig().isThinkSupported();
    }

    /**
     * Apply only static filters to tools -- any change kills the KV cache!
     * https://github.com/ggml-org/llama.cpp/issues/22746#issuecomment-4630455537
     */
    protected Predicate<SmartToolExecutor> getToolFilter() {
        return p -> true;
    }

    /**
     * Filters tools by name — applied to MCP tools, which {@link #getToolFilter()} cannot see.
     * Default: allow all. Overridden by custom agents to enforce their tool allowlist.
     */
    protected Predicate<String> getToolNameFilter() {
        return n -> true;
    }

    /** Reuses {@link #getToolFilter()} so it matches exactly what the agent sends at runtime. */
    @Override
    public boolean isToolActive(SmartToolExecutor exec) {
        return getToolFilter().test(exec);
    }

    /** Reuses {@link #getToolNameFilter()} (the MCP name allowlist). */
    @Override
    public boolean isMcpToolActive(String toolName) {
        return getToolNameFilter().test(toolName);
    }

    @Override
    public boolean isWorking() {
        return working.get();
    }

    /**
     * Queue a message for follow-up while the agent is working.
     * @return true if a new queue entry was created, false if silently merged into existing batch
     */
    @Override
    public boolean queueMessage(String msg) {
        return messageQueue.add(msg);
    }

    /** Test seam: swap the queue (fixed {@code Clock} for Rule 9 Queued-At tests). Package-private. */
    void setMessageQueue(UserMessageQueue queue) { this.messageQueue = queue; }

    public int tokenContextUsedInPercent() {
        float used = memory.getTotalTokenUsed();
        if (used < 100) return 0;
        return Math.round(100f * used / configuredModel.getConfig().getAutoCompactAfter());
    }

    /**
     * Token count at which this agent auto-compacts before a turn: the shared
     * {@link org.sterl.llmpeon.ai.LlmConfig#getAutoCompactAfter() budget} scaled by this agent's
     * {@code compactFactor} (1.0 = full budget; slaves use less, so they compact earlier).
     */
    public int compactAfterTokens() {
        return (int) Math.round(configuredModel.getConfig().getAutoCompactAfter() * compactFactor);
    }

    public boolean hasUserText(String message) {
        if (StringUtil.hasNoValue(message)) return true;
        return this.memory.containsUserMessage(message);
    }

    @Override
    public ChatResponse call(String initialMessage, AiMonitor monitor) {
        monitor = AiMonitor.nullSafety(monitor);
        try {
            // Self-enforcing guard: prevents concurrent invocations regardless of caller thread
            if (!working.compareAndSet(false, true)) {
                messageQueue.add(initialMessage); // already running: queue it
                return null;
            }

            var stillQueued = messageQueue.drainAll();
            String next;
            if (stillQueued == null) {
                next = initialMessage;
            } else if (StringUtil.hasValue(initialMessage)) {
                // Join path: queued payload + initial — no time here (Rule 9 shows it only on the marker)
                next = stillQueued.text() + System.lineSeparator() + initialMessage;
            } else {
                // Follow-up (null initial): the queue IS the payload — mark like in-loop pollNext
                next = queuedMarker(stillQueued);
            }

            ChatResponse lastResponse = null;
            do {
                try {
                    lastResponse = doCall(next, monitor);
                } catch (Exception e) {
                    handleAbortAndDrain(monitor);
                    throw e;
                }
                // check if we have waiting messages
                var queued = messageQueue.pollNext(); // FIFO drain
                if (queued != null) {
                    monitor.onTool("Reading queued User message: " + queued.text()
                            + " (queued " + messageQueue.queuedLabel(queued.queuedAt()) + ")");
                    next = queuedMarker(queued);
                } else {
                    next = null;
                }
            } while (next != null && lastResponse != null && !monitor.isCanceled());

            // Drain remaining queue on cancellation exit from loop
            if (monitor.isCanceled()) {
                handleAbortAndDrain(monitor);
            }

            return lastResponse;
        } finally {
            working.set(false);
        }
    }

    /** Drain remaining queued messages into memory on abort/error. */
    private void handleAbortAndDrain(AiMonitor monitor) {
        int preservedCount = messageQueue.size();
        var preserved = messageQueue.drainAll();
        if (preserved != null) {
            // Abort drain = memory payload — no time (Rule 9 shows it only on the onTool line + marker)
            memory.add(UserMessage.from(preserved.text()));
            monitor.onTool(preservedCount + " queued message(s) preserved for your next request.");
        }
    }

    @Override
    public String drainQueue() {
        // Interface contract stays String; the queue now carries the queuedAt timestamp (Rule 9)
        var drained = messageQueue.drainAll();
        return drained == null ? null : drained.text();
    }

    /** @return the number of queued messages waiting to be processed. */
    @Override
    public int getQueuedMessageCount() {
        return messageQueue.size();
    }

    /**
     * Rule 9 LLM marker: {@code [Queued Message] (HH:mm): <text>} — the message text stays
     * unchanged after the prefix; the time is rendered in the queue's clock zone.
     */
    private String queuedMarker(UserMessageQueue.QueuedMessage entry) {
        return UserMessageQueue.QUEUED_MARKER_PREFIX + "(" + messageQueue.queuedLabel(entry.queuedAt()) + "): " + entry.text();
    }

    /** Execute a single LLM+tool turn for the given message. */
    protected ChatResponse doCall(String message, AiMonitor monitor) {
        monitor = AiMonitor.nullSafety(monitor);
        monitor.onCallStart(message);
        // auto compress if we are close to full before we start (slaves trigger earlier via
        // compactFactor). R-CC-8: only above MIN_COMPACT_MESSAGES — below that a compact skips/fails.
        // R-CIB-1: autoCompactAfter <= 0 means "off" (like the Hint) — without this the gate would
        // fire every turn (0 < tokens) while the Stager never caps.
        if (configuredModel.getConfig().getAutoCompactAfter() > 0
                && compactAfterTokens() < memory.getTotalTokenUsed()
                && memory.size() > CompactConstants.MIN_COMPACT_MESSAGES) {
            monitor.onTool("Auto Compact before execution, context to full " + compactAfterTokens() + "/" + memory.getTotalTokenUsed());
            compact(monitor);
        }

        // Inject turn-scoped context on every turn (idempotent via contains-check)
        var userMessages = renderTurnContext(memory, turnContextSupplier, monitor);

        if (StringUtil.hasValue(message)) userMessages.add(TextContent.from(message));
        if (!userMessages.isEmpty()) addMessage(UserMessage.from(userMessages));

        var start = Instant.now();
        var staticMessages = buildStaticMessages(monitor);
        var response = toolService.executeLoop(
                ToolLoopRequest.builder()
                    .memory(memory)
                    .chatModel(configuredModel)
                    .staticMessages(staticMessages)
                    .monitor(monitor)
                    .toolFilter(getToolFilter())
                    .toolNameFilter(getToolNameFilter())
                    .writeValidator(getWriteValidator())
                    .agentConfig(getConfig())
                    .agent(this)
                    .build()
                );

        monitor.onCallCompleted(response, Duration.between(start, Instant.now()));
        return response;
    }

    @Override
    public CompactResult compact(AiMonitor monitor) {
        // User-triggered compact acquires the working flag (R-CT-1); an in-loop compact runs
        // inside a turn that already holds it, so the CAS fails and the flag is left untouched.
        boolean acquired = working.compareAndSet(false, true);
        try {
            // nullSafety before the guard: the FAILED_EMPTY onProblem path must never have a null monitor
            monitor = AiMonitor.nullSafety(monitor);
            // < MIN_COMPACT_MESSAGES: a compact leaves exactly 2 messages (Session-compacted user +
            // summary) — with < 2 a direct re-compact would fire a real LLM call on those 2 (R16)
            if (memory.size() < CompactConstants.MIN_COMPACT_MESSAGES) return CompactResult.skippedSmall();

            // R-CIB-1: the staging budget is the raw config value — compactFactor scales only the trigger
            // R-CC-12: capture the last provider-reported input tokens BEFORE the clear below —
            // after it the value is gone (async-state-safety)
            var requestTokens = memory.getLastProviderInputTokens();
            var messages = memory.getCopy();
            var compactCfg = configuredModel.getConfig().compactAgentConfig();
            // R-CC-14: the service is monitor-free — the start line is emitted by the agent
            // (all five triggers funnel through here), same wording as before
            monitor.onTool("Compressing conversation " + messages.size() + " messages "
                    + ChatMessageUtil.estimateTokens(messages) + " tokens"
                    + (compactCfg.getModel() == null ? "" : " using " + compactCfg.getModel()));
            var result = new CompactService(configuredModel).compact(
                    getName(), messages, memory.tokenDiagnosis(), requestTokens);

            if (result.status() == CompactResult.Status.FAILED_EMPTY) {
                monitor.onProblem("Compact failed: " + result.cause());
                return result;
            }

            memory.clear();
            this.systemMessage = null;
            // Restore turn-scoped context
            var data = renderTurnContext(memory, turnContextSupplier, monitor);
            // DON'T use addResult -> as the totalTokenUsed is from the compressor here which is to large
            // we only take the compacted new message!
            // and we remove the thinking, if any, from the result
            data.add(TextContent.from(CompactConstants.REINSERT_MARKER));
            // Ensure memory starts with a user message (many LLMs require this)
            memory.add(UserMessage.from(data));
            // we add the compact message as AI message
            memory.add(AiMessage.from(result.summary()));

            return result;
        } finally {
            if (acquired) working.set(false);
        }
    }

    /** Set static context items rendered into the system prompt on every rebuild. */
    public void setStaticContext(List<ContextItem> context) {
        this.staticContext = context;
        this.systemMessage = null;
    }

    @Override
    public List<ContextItem> getStaticContext() {
        return staticContext != null ? staticContext : List.of();
    }

    /** Set turn-scoped context supplier — items injected after compact or on first call. */
    public void setTurnContextSupplier(Supplier<List<ContextItem>> supplier) {
        this.turnContextSupplier = supplier;
    }

    @Override
    public void clear() {
        memory.clear();
        messageQueue.clear();
        this.systemMessage = null; // TODO test needed - AI forgot this reset case
    }

    /**
     * 1. System-Messages nur am Anfang erlaubt
     * 2. Tool-Messages NUR nach Assistant-Messages MIT tool_calls erlaubt
     * 3. Rollen müssen alternieren: user/assistant/user/assistant
     * 4. Nach User/System darf KEIN Tool kommen!
     */
    public void addMessage(ChatMessage message) {
        memory.add(message);
    }

    @Override
    public ToolService getToolService() {
        return toolService;
    }

    @Override
    public List<ChatMessage> buildStaticMessages(AiMonitor monitor) {
        var messages = new ArrayList<ChatMessage>();
        messages.add(SystemMessage.from(buildSystemPrompt(monitor)));
        return messages;
    }

    /**
     * Builds the full system prompt by appending rendered persistent context items
     * to the agent's base system prompt. Cached until invalidated (e.g. after compact).
     */
    private String buildSystemPrompt(AiMonitor monitor) {
        if (systemMessage != null) return systemMessage;

        var prompt = getSystemPrompt();
        if (staticContext != null) {
            for (var item : staticContext) {
                String rendered = item.render();
                if (rendered != null) {
                    if (StringUtil.hasValue(item.label())) {
                        monitor.onTool("📋 Loading " + item.label() + " (" + getName() + ")");
                    }
                    prompt = prompt + System.lineSeparator() + System.lineSeparator() + rendered;
                }
            }
        }
        this.systemMessage = prompt;
        return prompt;
    }

    /**
     * Restore turn-scoped context into memory after compact, skipping duplicates.
     * Keyed items ({@link ContextItem#dedupKey()}) are deduped by key BEFORE rendering;
     * unkeyed items fall back to rendered-content dedup.
     */
    static List<Content> renderTurnContext(
            ThreadSafeMemory memory,
            Supplier<List<ContextItem>> turnContextSupplier, 
            AiMonitor monitor) {

        var result = new LinkedList<Content>();

        if (turnContextSupplier == null) return result;
        var items = turnContextSupplier.get();
        if (items == null || items.isEmpty()) return result ;

        for (var item : items) {
            var key = item.dedupKey();
            if (key == null || !memory.containsUserMessage(key)) {
                String rendered = item.render();
                if (rendered == null) continue;
                if (memory.containsMessage(rendered)) continue;
                if (StringUtil.hasValue(item.label())) {
                    monitor.onTool("📋 Loading " + item.label());
                }
                if (key == null) result.add(new TextContent(rendered));
                else result.add(new TextContent(
                        key + System.lineSeparator() +
                        rendered + System.lineSeparator())
                    );
            }
        }
        return result;
    }
}
