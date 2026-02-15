package org.sterl.llmpeon.parts;

import java.util.List;

import org.sterl.llmpeon.parts.tools.SelectedFileTool;

import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.OllamaChatModel;

public class ChatService {
    private final ChatMemory memory = MessageWindowChatMemory.withMaxMessages(100);
    private final ChatModel model = OllamaChatModel.builder()
            .baseUrl("http://localhost:11434")
            .modelName("devstral-small-2:24b")
            .build();

    SelectedFileTool selectedFileTool;

    public ChatResponse sendMessage(String text) {
        if (text == null) return null;
        text = text.trim();
        if (text.isEmpty()) return null;

        
        memory.add(UserMessage.from(text));
        
        var request = ChatRequest.builder()
                .messages(memory.messages());

        // https://github.com/langchain4j/langchain4j/blob/main/docs/docs/tutorials/tools.md
        if (selectedFileTool != null) {
            request.toolSpecifications(ToolSpecifications.toolSpecificationsFrom(selectedFileTool));
        }
        
        var response = model.chat(request.build());
        memory.add(response.aiMessage());
        System.err.println(response.aiMessage());
        System.err.println(response.aiMessage().text());

        return response;
    }
    
    public List<ChatMessage> getMessages() {
        return memory.messages();
    }

    public void addTool(SelectedFileTool selectedFileTool) {
        this.selectedFileTool = selectedFileTool;
    }
}
