package org.sterl.llmpeon.memory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
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
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ThreadSafeMemory {

    private final LinkedList<ChatMessage> memory = new LinkedList<ChatMessage>();
    private volatile FileAgentHistoryStore store;
    @Getter
    private volatile int totalTokenUsed = 0;

    public ThreadSafeMemory() {
        this(null);
    }

    public ThreadSafeMemory(FileAgentHistoryStore store) {
        this.store = store;
        if (store != null) memory.addAll(store.load());
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
        if (message instanceof UserMessage num 
                && (!memory.isEmpty() && memory.getLast() instanceof UserMessage lum)) {
            memory.removeLast();
            memory.add(ChatMessageUtil.join(lum, num));
            persist(new ArrayList<>(memory));
        } else if (message instanceof UserMessage num 
                && (!memory.isEmpty() && memory.getLast() instanceof ToolExecutionResultMessage tR)) {
            // https://github.com/sterlp/eclipse-peon-ai/issues/87
            // this can happen e.g. or rate limits or server errors...
            log.warn("Detected tool result without AI response! {} - {}", tR.id(), tR.toolName());
            var repair = AiMessage.from("ok");
            memory.add(repair);
            memory.add(num);
            append(List.of(repair, num));
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

    public synchronized boolean containsUserMessage(String message) {
        if (StringUtil.hasNoValue(message)) return true;
        return memory.stream()
            .filter(m -> m instanceof UserMessage)
            .map(m -> (UserMessage)m)
            .anyMatch(um -> ChatMessageUtil.toString(um).contains(message));
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
        clearStore();
    }

    public synchronized void replaceAll(Collection<ChatMessage> messages) {
        memory.clear();
        if (messages != null) memory.addAll(messages);
        totalTokenUsed = 0;
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

    public synchronized void addResult(ChatResponse response, List<ToolExecutionResultMessage> toolResult) {
        var appended = new ArrayList<ChatMessage>();
        var aiMessage = response.aiMessage();
        memory.add(aiMessage);
        memory.addAll(toolResult);
        appended.add(aiMessage);
        appended.addAll(toolResult);
        totalTokenUsed = ChatMessageUtil.getTokenCount(response, memory);
        append(appended);
    }

    public synchronized void addResult(ChatResponse response) {
        var message = response.aiMessage();
        memory.add(message);
        totalTokenUsed = ChatMessageUtil.getTokenCount(response, memory);
        append(message);
    }

    public synchronized void forEach(Consumer<ChatMessage> consumer) {
        this.memory.forEach(consumer);
    }

    @Nullable
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
