package org.sterl.llmpeon.agent;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

public class AiDeveloperAgent implements AiAgent {

    private final String PROMT = """
            Embedded in Eclipse IDE. Today: %s.

            Tools:
            - Use tools automatically when applicable
            - Check currently selected file when no file context is given
            - Read project structure before creating files
            - Read matching SKILLs before starting a task
            - Compile and run tests to verify work
            - Avoid repeated tool calls for the same information
            
            Rules:
            - Never assume anything not confirmed by tool output or developer input
            - For complex tasks, create a plan and get developer approval before proceeding
            - If a needed tool doesn't exist, describe what the developer should implement
            - Ask when file placement or intent is ambiguous
            """;
    
    private final ChatModel chatModel;
    private final ChatRequest.Builder request = ChatRequest.builder();

    public AiDeveloperAgent(ChatModel chatModel) {
        super();
        this.chatModel = chatModel;
    }
    
    public ChatResponse call(List<ChatMessage> inMessages, AiMonitor monitor) {
        var messages = new ArrayList<ChatMessage>(inMessages);
        messages.addFirst(SystemMessage.from(PROMT
                .formatted(LocalDate.now().toString())));
        return chatModel.chat(request.messages(messages).build());
    }
    
    @Override
    public void withTools(List<ToolSpecification> tools) {
        request.toolSpecifications(tools);
    }
}