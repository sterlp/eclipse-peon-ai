package org.sterl.llmpeon.parts;

import java.util.List;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.OllamaChatModel;

public class ChatService {
    private final ChatMemory memory = MessageWindowChatMemory.withMaxMessages(100);
    private final ChatModel model = OllamaChatModel.builder()
            .baseUrl("http://localhost:11434")
            .modelName("devstral-small-2:24b")
            .build();

    
    public ChatResponse sendMessage(String text) {
        if (text == null) return null;
        text = text.trim();
        if (text.isEmpty()) return null;

        memory.add(UserMessage.from(text));
        
        var response = model.chat(memory.messages());
        memory.add(response.aiMessage());

        return response;
    }
    
    public List<ChatMessage> getMessages() {
        return memory.messages();
    }
}
