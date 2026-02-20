package org.sterl.llmpeon.agent;

import java.util.ArrayList;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

public class AiDeveloperAgent implements AiAgent {

    final SystemMessage system = SystemMessage.systemMessage("""
            You are a coding assistant helping developers solve technical tasks. Use available tools to access needed resources.
            Tool usage:
            - If a tool is available to do the job, automatically use it
            - Where is a tool to determine if the developer has currently a file selected 

            When information is missing:
            - Ask the developer directly
            - If a tool doesn't exist, describe what's needed and what the developer should implement
            - assume that the developer is talking about the current selected file if no other context is given and the tool returns a selected file

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
            - Search also for an AGENTS.md or Rules.md in case of a development task
            - Check the project structure before creating files
            """);
    
    private final ChatModel chatModel;

    public AiDeveloperAgent(ChatModel chatModel) {
        super();
        this.chatModel = chatModel;
    }
    
    public ChatResponse call(ChatRequest request, AiMonitor monitor) {
        var messages = new ArrayList<ChatMessage>();
        messages.add(system);
        messages.addAll(request.messages());
        request = request.toBuilder()
            .messages(messages)
            .build();
        return chatModel.chat(request);
    }
}