package org.sterl.llmpeon.agent;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.agent.model.TaggedResult;
import org.sterl.llmpeon.ai.AgentConfig;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.compact.CompactConstants;
import org.sterl.llmpeon.compact.CompactFeedback;
import org.sterl.llmpeon.compact.CompactService;
import org.sterl.llmpeon.compact.HandleToolLoopCompactComponent;
import org.sterl.llmpeon.exception.ExceptionUtil;
import org.sterl.llmpeon.model.CompactResult;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.queuedmessages.QueuedUserMessages;
import org.sterl.llmpeon.queuedmessages.UserMessageQueue;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.CallStats;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.shared.OwnerMonitor;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.LoopOwner;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.ToolPolicy;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;

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
    /** R-CT-4: own compact indicator for the roster 🟡 — display only, the lock stays {@code working}.
     *  Volatile: the header reads it from the UI thread while a compact job sets it. */
    private volatile boolean compacting = false;

    /** R20: start of the current turn. Owned by the agent; set only when the working flag is acquired. */
    private volatile Instant turnStart;

    private Clock clock = Clock.systemUTC();

    private volatile String systemMessage = null;
    /**
     * Build only once
     */
    private List<ContextItem> staticContext;
    private Supplier<List<ContextItem>> turnContextSupplier;

    protected AbstractAgent(ConfiguredChatModel configuredModel, ToolService toolService) {
        this(configuredModel, toolService, new ThreadSafeMemory());
    }

    protected AbstractAgent(ConfiguredChatModel configuredModel, ToolService toolService, ThreadSafeMemory memory) {
        this.toolService = toolService;
        this.configuredModel = configuredModel;
        this.memory = Objects.requireNonNull(memory, "ThreadSafeMemory cannot be null");

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
     * The agent slot whose stored tool allowlist ({@code llm.agent.<id>.tools}) applies to this
     * agent; {@code null} = no allowlist (all tools). Dev/Plan/PO/Review override with their slot.
     */
    protected String toolAllowlistSlot() {
        return null;
    }

    /**
     * Class-based tool restriction (e.g. no edit tools for Plan). The stored allowlist can only
     * NARROW this set, never widen it: the effective filter is {@code classFilter ∧ allowlist}.
     */
    protected Predicate<SmartToolExecutor> classFilter() {
        return t -> true;
    }

    /**
     * The ONE composition (R-ATC-8): the runtime filter and the preview both build on it —
     * the class filter AND the allowlist parsed from the given raw CSV.
     */
    private Predicate<SmartToolExecutor> filterFor(String rawCsv) {
        var allow = ToolPolicy.nameFilter(rawCsv);
        var cls = classFilter();
        return exec -> cls.test(exec) && allow.test(exec.getSpec().name());
    }

    /**
     * Filters the built-in tools this agent may call. The allowlist is read <b>per call</b> from
     * the live config — persistent agents are NOT rebuilt on a config change (R-ATC-7), so a
     * per-call read is the only way a stored allowlist takes effect without a restart.
     *
     * <p>The set only changes on config changes (like any tool-set change) and stays stable
     * within a session — the KV-cache caveat still applies to config edits:
     * https://github.com/ggml-org/llama.cpp/issues/22746#issuecomment-4630455537
     */
    protected Predicate<SmartToolExecutor> getToolFilter() {
        return filterFor(storedAllowlist());
    }

    /**
     * Filters tool names — applied to MCP specs, which {@link #getToolFilter()} cannot see;
     * together both apply the allowlist to ALL tools (built-in + MCP, R-ATC-6). Read per call
     * like {@link #getToolFilter()}.
     */
    protected Predicate<String> getToolNameFilter() {
        return ToolPolicy.nameFilter(storedAllowlist());
    }

    /** The stored raw CSV for this agent's slot; {@code null} when the slot is unset or the key absent. */
    private String storedAllowlist() {
        var slot = toolAllowlistSlot();
        return slot == null ? null : configuredModel.getConfig().toolAllowlist(slot);
    }

    /**
     * Preview (R-ATC-8): the sorted names of ALL tools (built-in + MCP) that would be active for the
     * GIVEN raw CSV — via the shared {@link ToolService#activeNames(Predicate, Predicate)}, the exact
     * composition the runtime filter uses, so the preview can never drift from what the agent
     * actually sends. The CSV is the value the user typed, not necessarily the stored one.
     */
    public List<String> resolveActiveTools(String rawCsv) {
        return toolService.activeNames(classFilter(), ToolPolicy.nameFilter(rawCsv));
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

    /** R-CT-4: true only while a compact actually runs (never for the R16 skip) — the roster 🟡. */
    @Override
    public boolean isCompacting() {
        return compacting;
    }

    /**
     * Queue a message for follow-up while the agent is working.
     * @return true if a new queue entry was created, false if silently merged into existing batch
     */
    @Override
    public boolean queueMessage(String msg) {
        return messageQueue.add(msg);
    }

    /**
     * Rule 8: the urgent lane of the agent's own queue — under the queue's monitor, which is the
     * monitor of the turn-slot hand-over (Rule 10), so a „!" sent while the turn releases is taken
     * by that turn instead of stranding. Never a memory write.
     */
    @Override
    public void queueUrgentMessage(String msg) {
        synchronized (messageQueue) {
            messageQueue.addUrgent(msg);
        }
    }

    /**
     * Rule 11: drain + disclosure are <b>one</b> step under the queue's monitor — a consumption that
     * is not visible is exactly the silent injection the rule forbids, and a drain outside that
     * monitor would race the turn-slot hand-over (Rule 10).
     */
    @Override
    public List<String> takeUrgentMessages(AiMonitor monitor) {
        var mon = AiMonitor.nullSafety(monitor);
        synchronized (messageQueue) {
            var entries = messageQueue.drainUrgent();
            for (var entry : entries) {
                mon.onTool(QueuedUserMessages.readingLine(messageQueue, entry));
            }
            return entries.stream().map(entry -> QueuedUserMessages.bodyLine(messageQueue, entry)).toList();
        }
    }

    /** Test seam: swap the queue (fixed {@code Clock} for Rule 9 Queued-At tests). Package-private.
     *  The instance is also the monitor of the turn-slot hand-over — swap it before a turn, never
     *  during one, or claim and release stop being ordered against the enqueues. */
    void setMessageQueue(UserMessageQueue queue) { this.messageQueue = queue; }

    /** Test seam: fixed clock for R20 turn-time tests. Package-private. */
    void setClock(Clock clock) { this.clock = clock; }

    public int tokenContextUsedInPercent() {
        float used = memory.getTotalTokenUsed();
        if (used < 100) return 0;
        return Math.round(100f * used / configuredModel.getConfig().getAutoCompactAfter());
    }

    public boolean hasUserText(String message) {
        if (StringUtil.hasNoValue(message)) return true;
        return this.memory.containsUserMessage(message);
    }

    /**
     * Stamps every line emitted below this entry with our id — the one place a turn's own lines get
     * their owner (ADR-0078). Nested producers wrap it again and win, see {@link OwnerMonitor}.
     */
    private AiMonitor stamped(AiMonitor monitor) {
        return new OwnerMonitor(getName(), monitor);
    }

    @Override
    public ChatResponse call(String initialMessage, AiMonitor monitor) {
        monitor = stamped(monitor);
        // Self-enforcing slot claim, atomic with the enqueue under the queue's monitor — contract and
        // boundary (claim side): Rule 10, org.sterl.llmpeon.core/docs/queued-user-messages.md
        boolean acquired = claimTurnSlotOrQueue(initialMessage);
        if (acquired) turnStart = Instant.now(clock);
        boolean slotReleased = false; // the loop's hand-over may already have released it
        try {
            if (!acquired) return null;

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
                var queued = messageQueue.pollNext();
                if (queued == null && lastResponse != null && !monitor.isCanceled()) {
                    // nothing polled and the turn is over: hand the slot over — under the queue's
                    // monitor, so an input that arrived since the poll stays ours to answer
                    queued = nextQueuedEntryOrReleaseSlot();
                    slotReleased = queued == null;
                }
                if (queued != null) {
                    monitor.onTool(QueuedUserMessages.readingLine(messageQueue, queued));
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
            if (acquired && !slotReleased) working.set(false);
        }
    }

    /**
     * Takes the turn slot, or hands the input to whoever holds it — both under the queue's monitor,
     * which is the monitor every enqueue takes. That single critical section is what closes the
     * release window: a caller either finds the slot free and runs its own turn, or it joins a turn
     * that provably has not released yet and therefore still has a look at the queue coming. Claim in
     * one section and queue in the next, and an input could be rejected by a turn that released in
     * between and then queued behind that release — waiting for nobody.
     *
     * @return true if the caller owns the slot; false if the input is queued for the running turn
     */
    private boolean claimTurnSlotOrQueue(String initialMessage) {
        synchronized (messageQueue) {
            if (working.compareAndSet(false, true)) return true;
            messageQueue.add(initialMessage); // already running: that turn answers it
            return false;
        }
    }

    /**
     * Ends a turn's claim on its queue atomically with the last look at it: under the queue's monitor
     * it returns the entry that arrived since the caller's poll, or releases {@code working} when the
     * queue really is empty (only the slot holder polls, so an empty poll means empty queue).
     * <p>
     * Releasing without that atomicity left an input that arrived between the last
     * {@code pollNext()} and {@code working.set(false)} waiting for a turn that had already ended: no
     * answer, and with one {@code done} per turn (aifactory {@code ProjectRuntime#submit}) no
     * end-of-turn event either — it waited until an unrelated send picked it up.
     *
     * @return the entry this turn still has to answer; {@code null} means the slot was released
     */
    private UserMessageQueue.QueuedMessage nextQueuedEntryOrReleaseSlot() {
        synchronized (messageQueue) {
            var entry = messageQueue.pollNext();
            if (entry == null) working.set(false);
            return entry;
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
     * Rule 9 LLM marker — rendered by {@link QueuedUserMessages}, the single home of the format,
     * so the payload marker and the disclosure line of an entry can never disagree.
     */
    private String queuedMarker(UserMessageQueue.QueuedMessage entry) {
        return QueuedUserMessages.marker(messageQueue, entry);
    }

    /** Execute a single LLM+tool turn for the given message. */
    protected ChatResponse doCall(String message, AiMonitor monitor) {
        var preTurn = preTurnCompact(monitor); // R-CC-20.2: over 95 % of the raw budget, before the fresh message enters
        // Inject turn-scoped context on every turn (idempotent via contains-check)
        var userMessages = renderTurnContext(memory, turnContextSupplier, monitor);

        if (StringUtil.hasValue(message)) userMessages.add(TextContent.from(message));
        if (!userMessages.isEmpty()) addMessage(UserMessage.from(userMessages));
        // R-CT-7: the pre-turn compact is announced only after the memory add — a listener rebuilding
        // the UI from the memory then sees summary + fresh message (UC-CT-16).
        if (preTurn != null) announce(monitor, preTurn);
        monitor.onCallStart(message);

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
                    .owner(LoopOwner.of(this, base -> getConfig()))
                    .turnStart(turnStart)
                    .build()
                );

        var duration = Duration.between(start, Instant.now());
        monitor.onCallCompleted(response, duration);
        return response;
    }
    
    /** Execute a single LLM+tool turn for the given message. */
    protected ChatResponse asyncCall(String message, AiMonitor monitor) {
        var preTurn = preTurnCompact(monitor); // R-CC-20.2: over 95 % of the raw budget, before the fresh message enters
        // Inject turn-scoped context on every turn (idempotent via contains-check)
        var userMessages = renderTurnContext(memory, turnContextSupplier, monitor);

        if (StringUtil.hasValue(message)) userMessages.add(TextContent.from(message));
        if (!userMessages.isEmpty()) addMessage(UserMessage.from(userMessages));
        // R-CT-7: the pre-turn compact is announced only after the memory add — a listener rebuilding
        // the UI from the memory then sees summary + fresh message (UC-CT-16).
        if (preTurn != null) announce(monitor, preTurn);
        monitor.onCallStart(message);

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
                    .owner(LoopOwner.of(this, base -> getConfig()))
                    .turnStart(turnStart)
                    .build()
                );

        return response;
    }

    /**
     * R-CC-20.2: the pre-turn over-budget check — over the 95 % band of the raw budget
     * ({@link HandleToolLoopCompactComponent#overHintBand(int, int, int)}), run before the fresh
     * user message enters the memory, so a fresh message is never lost to a compact (ADR-0010).
     * One attempt per turn, no hint, no retry. The in-loop direct compact keeps its own 100 % rule
     * (R-CC-18.1).
     */
    private @Nullable CompactRun preTurnCompact(AiMonitor monitor) {
        if (!HandleToolLoopCompactComponent.overHintBand(memory.getTotalTokenUsed(),
                configuredModel.getConfig().getAutoCompactAfter(), memory.size())) return null;
        CompactRun run;
        try {
            run = runCompact(monitor); // in-turn: the CAS leaves the turn's working flag untouched
        } catch (RuntimeException e) {
            // R-CC-20.5: a failing compressor must not kill the turn before the fresh message enters.
            // Cancellation is not a failure — a Stop still aborts the turn (R-CC-7 owns retry/classes).
            if (ExceptionUtil.isCanceled(e)) throw e;
            AiMonitor.nullSafety(monitor).onProblem("Pre-turn compact failed: " + e.getMessage());
            return null; // over budget only: the provider call is the honest outcome (same as FAILED_EMPTY)
        }
        // SKIPPED_SMALL ran nothing: no event. FAILED_EMPTY/COMPACTED: no retry, no hint, the caller
        // announces after the fresh message entered the memory (R-CT-7).
        if (run.result().status() == CompactResult.Status.SKIPPED_SMALL) return null;
        if (run.result().status() == CompactResult.Status.COMPACTED) {
            memory.reevaluateTokens(); // R-CC-2 — the end line's figures were captured at the compact
        }
        return run;
    }

    @Override
    public CompactResult compact(AiMonitor monitor) {
        var mon = stamped(monitor);
        var run = runCompact(mon);
        announce(mon, run);
        return run.result();
    }

    /**
     * A finished compact run — result plus the figures the end line needs, captured at compact time:
     * a pre-turn compact announces only after the fresh message entered the memory (R-CT-7), by then
     * the memory is bigger than "the result".
     */
    private record CompactRun(CompactResult result, CallStats stats, int messageCountAfter,
                              int tokensAfter) {}

    /** Runs the compact incl. the working-flag handling; announces nothing. */
    private CompactRun runCompact(AiMonitor monitor) {
        // ADR-0053: the shared duration/wall-time helper, started at the compact's visible start so
        // the end line measures everything the user waits for — staging, call and re-seed.
        var callStats = CallStats.start();
        // null-safe up front: the FAILED_EMPTY onProblem path must never have a null monitor
        var mon = AiMonitor.nullSafety(monitor);
        // User-triggered compact acquires the working flag (R-CT-1); an in-loop compact runs
        // inside a turn that already holds it, so the CAS fails and the flag is left untouched.
        boolean acquired = working.compareAndSet(false, true);
        if (acquired) turnStart = Instant.now(clock);
        CompactResult result;
        try {
            result = doCompact(mon);
        } finally {
            if (acquired) working.set(false);
        }
        return new CompactRun(result, callStats, memory.size(), memory.getTotalTokenUsed());
    }

    /**
     * R-CT-7: the one reset channel for every trigger. A skip ran nothing (no event), and a throw
     * never reaches an announce.
     */
    private void announce(AiMonitor monitor, CompactRun run) {
        var mon = AiMonitor.nullSafety(monitor);
        var result = run.result();
        if (result.status() != CompactResult.Status.SKIPPED_SMALL) {
            mon.onCompactCompleted(getName(), result);
        }
        // R-CT-8: the one end line, for every trigger and every agent alike. It goes out after the
        // event on purpose — the event enqueues the chat rebuild of the shown agent (R-CT-7),
        // and SWT runs the queued display FIFO, so the line survives that rebuild instead of being
        // reset away with it.
        if (result.status() == CompactResult.Status.COMPACTED) {
            mon.onTool(CompactFeedback.endLine(getName(), result, run.messageCountAfter(),
                    run.tokensAfter(), run.stats()));
        }
    }

    /** The compact run itself — {@code monitor} is already null-safe. */
    private CompactResult doCompact(AiMonitor monitor) {
        // < MIN_COMPACT_MESSAGES: a successful compact re-seeds exactly ONE UserMessage whose last
        // TextContent is the reinsert marker + summary (R-CC-9) — a direct re-compact would compress
        // its own summary, so the guard has to stay above 1 (R16).
        // The compacting flag is set only behind this guard: a skip shows no 🟡 (R-CT-4).
        if (memory.size() < CompactConstants.MIN_COMPACT_MESSAGES) return CompactResult.skippedSmall();

        compacting = true;
        try {
            // R-CIB-1: the staging budget is the raw config value
            // R-CC-12: capture the last provider-reported input tokens BEFORE the clear below —
            // after it the value is gone (async-state-safety)
            var requestTokens = memory.getLastProviderInputTokens();
            var messages = memory.getCopy();
            var compactCfg = configuredModel.getConfig().compactAgentConfig();
            // R-CC-14: the service emits no lines itself — the start line is emitted by the agent
            // (all triggers funnel through here), and this monitor is the one the compressor streams
            // its tokens into, so the caller sees a real working state (R-CT-5, R-CT-8)
            monitor.onTool(CompactFeedback.startLine(getName(), messages.size(),
                    ChatMessageUtil.estimateTokens(messages), compactCfg.getModel())); // R-CT-6
            var result = new CompactService(configuredModel).compact(
                    getName(), messages, memory.tokenDiagnosis(), requestTokens, monitor, turnStart);

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
            // Ensure memory starts with a user message (many LLMs require this)
            data.add(TextContent.from(CompactConstants.REINSERT_MARKER
                    + System.lineSeparator() + System.lineSeparator()
                    + result.summary())
            );
            memory.add(UserMessage.from(data));
            return result;
        } finally {
            compacting = false;
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
        // Runs outside a turn too (compact tooling) — the 📋 Loading lines still belong to us.
        var messages = new ArrayList<ChatMessage>();
        messages.add(SystemMessage.from(buildSystemPrompt(stamped(monitor))));
        return messages;
    }

    /**
     * Builds the full system prompt by appending the rendered persistent context items
     * (body-only) to the agent's base system prompt, plus the harness-block format info
     * ({@link TaggedResult#INFO}) exactly once (R-TR-6) — every agent can see tagged blocks.
     * Cached until invalidated (e.g. after compact).
     */
    private String buildSystemPrompt(AiMonitor monitor) {
        if (systemMessage != null) return systemMessage;

        var prompt = getSystemPrompt();
        if (staticContext != null) {
            for (var item : staticContext) {
                var rendered = item.render();
                if (rendered != null) {
                    if (StringUtil.hasValue(item.label())) {
                        monitor.onTool("📋 Loading " + item.label() + " (" + getName() + ")");
                    }
                    prompt = prompt + System.lineSeparator() + System.lineSeparator() + rendered.body();
                }
            }
        }
        prompt = prompt + System.lineSeparator() + System.lineSeparator() + TaggedResult.INFO;
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
                var rendered = item.render();
                if (rendered == null) continue;
                if (memory.containsMessage(rendered.toString())) continue;
                if (StringUtil.hasValue(item.label())) {
                    monitor.onTool("📋 Loading " + item.label());
                }
                result.add(TextContent.from(rendered.toString()));
            }
        }
        return result;
    }
}
