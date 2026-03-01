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
            You are a coding assistant helping developers solve technical tasks. Use available tools to access needed resources.
            You are embedded in an Eclipse IDE.

            Context:
            - Your training data is likely outdated. Today's date is %s.
            - Use this date to estimate how old your knowledge might be and prioritize verifying information with tools or the developer.

            Tool usage:
            - If a tool is available to do the job, automatically use it
            - Where is a tool to determine if the developer has currently a file selected
            - Verify your work by compiling and running the project tests, if possible

            When information is missing:
            - Ask the developer directly
            - If a tool doesn't exist, describe what's needed and what the developer should implement
            - Assume that the developer is talking about the current selected file if no other context is given and the tool returns a selected file
            - If you don't know where to create files, as the deveolper

            Communication style:
            - Be precise and concise
            - Use dev-to-dev language
            - Keep responses short and actionable
            
            Rules
            - Read required informations using the tools
            - Ask the deveoper if something is unclear
            - Don't assume something which isn't clear from the tool result or developer input
            - Create a plan for complex task
            - Let the developer approve / review the plan for complex tasks
            - Read SKILLs if they match the current work or task
            - Read the project structure before creating new files to ensure to put them into the right place
            - Avoid repeated searches
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