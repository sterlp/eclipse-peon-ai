package org.sterl.llmpeon.memory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;
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
    @Getter
    private volatile int totalTokenUsed = 0;
    
    /**
     * 1. System-Messages nur am Anfang erlaubt
     * 2. Tool-Messages NUR nach Assistant-Messages MIT tool_calls erlaubt
     * 3. Rollen müssen alternieren: user/assistant/user/assistant
     * 4. Nach User/System darf KEIN Tool kommen!
     */
    public synchronized ThreadSafeMemory add(ChatMessage message) {
        if (message instanceof UserMessage num 
                && (!memory.isEmpty() && memory.getLast() instanceof UserMessage lum)) {
            memory.removeLast();
            memory.add(ChatMessageUtil.join(lum, num));
        } else {
            memory.add(message); 
        }
        return this;
    }
    
    public boolean containsUserMessage(String message) {
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

    public int size() {
        return memory.size();
    }
    
    public synchronized void clear() {
        memory.clear();
        totalTokenUsed = 0;
    }
    
    public void printMessages() {
        String flow = messageFlow();
        log.info("Memory message types: {}", flow);
    }

    public synchronized String messageFlow() {
        String flow = memory.stream()
                .map(m -> m.type().name())
                .collect(Collectors.joining("->"));
        return flow;
    }

    public synchronized void addResult(ChatResponse response, List<ToolExecutionResultMessage> toolResult) {
        memory.add(response.aiMessage());
        memory.addAll(toolResult);
        totalTokenUsed = ChatMessageUtil.getTokenCount(response, memory);
    }

    public synchronized void addResult(ChatResponse response) {
        memory.add(response.aiMessage());
        totalTokenUsed = ChatMessageUtil.getTokenCount(response, memory);
    }

    public void forEach(Consumer<ChatMessage> consumer) {
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
}
