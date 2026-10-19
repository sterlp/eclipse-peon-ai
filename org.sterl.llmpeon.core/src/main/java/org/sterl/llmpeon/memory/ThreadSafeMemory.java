package org.sterl.llmpeon.memory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ThreadSafeMemory {

    private final LinkedList<ChatMessage> memory = new LinkedList<ChatMessage>();
    private volatile FileAgentHistoryStore store;
    @Getter
    private volatile int totalTokenUsed = 0;

    /**
     * Whether {@link #totalTokenUsed} currently contains an estimate component (R-CC-6,
     * docs/compact-context-counter.md). Set by every write site: estimate-driven writes
     * (constructor restore, {@code add}, {@code reevaluateTokens}, {@code addResult}) mark it
     * true — after {@code addResult} the value is the provider input plus the chars/3 estimate of
     * the appended messages (R-CC-19), so it always carries an estimate component. The reset to 0
     * in {@code clear}/{@code replaceAll} marks it false — 0 is exact.
     */
    private volatile boolean tokenIsEstimate = false;

    /**
     * R-CC-10: the LAST provider-REPORTED {@code inputTokenCount()} — what the model actually saw
     * on its last call. Set only when the provider reports usage; a response without usage leaves
     * the previous value in place ("last reported"); reset to null by {@code clear()}/{@code replaceAll()}.
     * Pure observation — never feeds {@link #totalTokenUsed}, the gates or the display.
     */
    private volatile @Nullable Integer lastProviderInputTokens = null;

    /**
     * R-CC-18 (D3): one-shot mark that the NEXT {@code addResult}'s provider
     * {@code inputTokenCount} describes the PRE-compact context. Volatile like the sibling
     * flags — the memory is read from UI threads. Deliberately NOT set by {@code clear()}/
     * {@code replaceAll()} (too coarse — those are followed by a FRESH provider value, e.g.
     * after the pre-turn compact, which must be consumed as usual).
     */
    private volatile boolean providerUsageStale = false;

    /** @return true if the current token counter value contains an estimate component. */
    public boolean isTokenEstimate() {
        return tokenIsEstimate;
    }

    /**
     * Next {@code addResult}'s provider {@code inputTokenCount} describes the PRE-compact context
     * — derive from estimate instead. Set by {@code CompactSessionTool} on COMPACTED; consumed by
     * the next {@code addResult}.
     */
    public void markProviderUsageStale() {
        providerUsageStale = true;
    }

    /** R-CC-10: the last provider-reported input token count, or null when never reported/reset. */
    public @Nullable Integer getLastProviderInputTokens() {
        return lastProviderInputTokens;
    }

    /**
     * R-CC-10: the compact-log diagnosis — the three context sizes side by side, each with its
     * source/flag. Appended by ALL three compact event lines (hint, entry, skip). One format, one
     * implementation: {@code " | memory=<total>(estimate=<flag>) model=<input|n/a> estimate=<chars/3>"}.
     */
    public synchronized String tokenDiagnosis() {
        String model = lastProviderInputTokens == null ? "n/a" : String.valueOf(lastProviderInputTokens);
        return " | memory=" + totalTokenUsed + "(estimate=" + tokenIsEstimate + ") model=" + model
                + " estimate=" + ChatMessageUtil.estimateTokens(getCopy());
    }

    public ThreadSafeMemory() {
        this(null);
    }

    public ThreadSafeMemory(FileAgentHistoryStore store) {
        this.store = store;
        if (store != null) {
            memory.addAll(store.load());
            // getTokenCount(null, …) already returns the chars/3 estimate — no second /3
            totalTokenUsed = ChatMessageUtil.getTokenCount(null, new ArrayList<>(memory));
            tokenIsEstimate = !memory.isEmpty(); // empty restore → 0, which is exact
        }
    }

    /**
     * 1. System-Messages nur am Anfang erlaubt
     * 2. Tool-Messages NUR nach Assistant-Messages MIT tool_calls erlaubt
     * 3. Rollen müssen alternieren: user/assistant/user/assistant
     * 4. Nach User/System darf KEIN Tool kommen!
     * 
     * https://developers.openai.com/api/docs/guides/function-calling
     */
    public synchronized ThreadSafeMemory add(ChatMessage message) {
        totalTokenUsed += ChatMessageUtil.estimateTokens(List.of(message));
        tokenIsEstimate = true;

        if (message instanceof UserMessage num 
                && (!memory.isEmpty() && memory.getLast() instanceof UserMessage lum)) {
            memory.removeLast();
            memory.add(ChatMessageUtil.join(lum, num));
            persist(new ArrayList<>(memory));
        } else if (message instanceof UserMessage num 
                && (!memory.isEmpty() && memory.getLast() instanceof ToolExecutionResultMessage tR)) {
            // https://github.com/sterlp/eclipse-peon-ai/issues/87
            // this can happen e.g. or rate limits or server errors...
            log.warn("Detected tool result without AI response but a Usermessage is added! {} - {} - user message:\n{}", 
                    tR.id(), tR.toolName(), num);
            var toAdd = List.of(AiMessage.from("ok"), num);
            memory.addAll(toAdd);
            append(toAdd);
        } else {
            memory.add(message);
            append(message);
        }
        return this;
    }
    
    /** @return true if this memory is backed by a history store (durable), false if RAM-only. */
    public boolean isPersistent() {
        return store != null;
    }

    /**
     * @return the history file this memory persists to (empty for RAM-only). Serves the tests and
     * callers that need the concrete location without reaching into the store.
     */
    public Optional<Path> historyFile() {
        var s = store;
        return s != null ? Optional.of(s.historyFile()) : Optional.empty();
    }

    public synchronized boolean containsUserMessage(String message) {
        if (StringUtil.hasNoValue(message)) return true;
        return memory.stream()
            .filter(m -> m instanceof UserMessage)
            .map(m -> (UserMessage)m)
            .anyMatch(um -> ChatMessageUtil.toString(um).contains(message));
    }
    
    public synchronized boolean containsMessage(String message) {
        if (StringUtil.hasNoValue(message)) return true;
        return memory.stream()
            .filter(m -> m instanceof UserMessage || m instanceof ToolExecutionResultMessage)
            .map(m -> ChatMessageUtil.toString(m))
            .anyMatch(um -> um.contains(message));
    }
    
    public synchronized List<ChatMessage> getCopy() {
        return new ArrayList<>(memory);
    }
    
    public synchronized void addMemoryTo(Collection<ChatMessage> list) {
        list.addAll(memory);
    }

    public synchronized int size() {
        return memory.size();
    }
    
    public synchronized void clear() {
        memory.clear();
        totalTokenUsed = 0;
        tokenIsEstimate = false;
        lastProviderInputTokens = null;
        clearStore();
    }

    public synchronized void replaceAll(Collection<ChatMessage> messages) {
        memory.clear();
        if (messages != null) memory.addAll(messages);
        totalTokenUsed = 0;
        tokenIsEstimate = false;
        lastProviderInputTokens = null;
        persist(new ArrayList<>(memory));
    }
    
    public void printMessages() {
        String flow = messageFlow();
        log.info("Memory message types: {}", flow);
    }

    public synchronized String messageFlow() {
        String flow = memory.stream()
                .map(this::messageType)
                .collect(Collectors.joining("->"));
        return flow;
    }
    
    private String messageType(ChatMessage cm) {
        if (cm == null) return "";
        if (cm instanceof AiMessage ai && ai.hasToolExecutionRequests()) return "TOOL_REQUEST";
        return cm.type().name();
    }

    /** Re-derives the token counter from the actual memory content (e.g. after a compact). */
    public synchronized void reevaluateTokens() {
        totalTokenUsed = ChatMessageUtil.estimateTokens(getCopy());
        tokenIsEstimate = true;
    }

    public synchronized void addResult(ChatResponse response, List<ToolExecutionResultMessage> toolResult) {
        var appended = new ArrayList<ChatMessage>();
        var aiMessage = response.aiMessage();
        memory.add(aiMessage);
        memory.addAll(toolResult);
        appended.add(aiMessage);
        appended.addAll(toolResult);
        if (providerUsageStale) {
            // R-CC-18 (D3): this response's provider value describes the PRE-compact context —
            // keep the small post-compact estimate; lastProviderInputTokens stays cleared
            providerUsageStale = false;
            reevaluateTokens();
        } else {
            updateTokenCounter(ChatMessageUtil.tokenUsage(response), appended);
        }
        append(appended);
    }

    public synchronized void addResult(ChatResponse response) {
        var message = response.aiMessage();
        memory.add(message);
        if (providerUsageStale) {
            // R-CC-18 (D3): this response's provider value describes the PRE-compact context —
            // keep the small post-compact estimate; lastProviderInputTokens stays cleared
            providerUsageStale = false;
            reevaluateTokens();
        } else {
            updateTokenCounter(ChatMessageUtil.tokenUsage(response), List.of(message));
        }
        append(message);
    }

    /**
     * R-CC-19 (docs/compact.md): the counter is forward-looking — the provider
     * {@code inputTokenCount} of the last request plus the chars/3 estimate of everything
     * appended since it (the AI message with the tool calls goes out again on the next request,
     * and so do this round's tool results). The provider value alone is the PREVIOUS request —
     * a large tool result would push the NEXT request over the limit while a stale counter stays
     * under budget. Without a provider value (local model) the fallback is the chars/3 estimate
     * of the whole memory — recomputed, never added, so already-estimated messages are not
     * double-counted.
     */
    private void updateTokenCounter(@Nullable TokenUsage usage, List<ChatMessage> appended) {
        var input = usage != null ? usage.inputTokenCount() : null;
        if (input != null) {
            totalTokenUsed = input + ChatMessageUtil.estimateTokens(appended);
            lastProviderInputTokens = input; // R-CC-10: pure observation, unchanged
        } else {
            totalTokenUsed = ChatMessageUtil.estimateTokens(getCopy());
        }
        tokenIsEstimate = true; // an estimate component is always present after addResult
    }

    public synchronized void forEach(Consumer<ChatMessage> consumer) {
        this.memory.forEach(consumer);
    }

    @Nullable
    @SuppressWarnings("unchecked") // isInstance-guarded, so the cast to T is safe
    public synchronized <T extends ChatMessage> T getLastOf(Class<T> type) {
        var it = memory.listIterator(memory.size());
        while (it.hasPrevious()) {
            ChatMessage m = it.previous();
            if (type.isInstance(m)) {
                return (T)m;
            }
        }
        return null;
    }

    public synchronized ChatMessage get(int index) {
        return this.memory.get(index);
    }
    
    private void append(ChatMessage message) {
        var s = store;
        if (s == null) return;
        try {
            s.append(message);
        } catch (IOException e) {
            store = null;
            throw new RuntimeException("Failed to append chat history", e);
        }
    }

    private void append(List<ChatMessage> messages) {
        var s = store;
        if (s == null) return;
        try {
            s.append(messages);
        } catch (IOException e) {
            store = null;
            throw new RuntimeException("Failed to append chat history", e);
        }
    }

    private void persist(List<ChatMessage> messages) {
        var s = store;
        if (s == null) return;
        try {
            s.persist(messages);
        } catch (IOException e) {
            store = null;
            throw new RuntimeException("Failed to persist chat history", e);
        }
    }

    private void clearStore() {
        var s = store;
        if (s == null) return;
        try {
            s.clear();
        } catch (IOException e) {
            store = null;
            throw new RuntimeException("Failed to clear chat history", e);
        }
    }
}
