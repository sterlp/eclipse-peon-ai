package org.sterl.llmpeon.agent;

import java.util.ArrayList;
import java.util.List;

import org.sterl.llmpeon.template.TemplateContext;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

public class AiDeveloperAgent implements AiAgent {

    private static final String BASE_PROMPT = """
            Embedded in Eclipse IDE. Today: ${currentDate}. Working in: ${workPath}.

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

    private static final String AGENT_MODE_ADDITION = """

            AGENT MODE — if you cannot proceed after 2 attempts (build failure, missing context,
            conflicting requirements), call reportProblem with a detailed description.
            Do not retry indefinitely. Escalate early so the plan agent can revise the plan.
            """;

    private final String PROMT;

    private final ChatModel chatModel;
    private final TemplateContext context;
    private final ChatRequest.Builder request = ChatRequest.builder();

    public AiDeveloperAgent(ChatModel chatModel, TemplateContext context) {
        this(chatModel, context, false);
    }

    public AiDeveloperAgent(ChatModel chatModel, TemplateContext context, boolean agentMode) {
        this.chatModel = chatModel;
        this.context = context;
        this.PROMT = agentMode ? BASE_PROMPT + AGENT_MODE_ADDITION : BASE_PROMPT;
    }

    public ChatResponse call(List<ChatMessage> inMessages, AiMonitor monitor) {
        var messages = new ArrayList<ChatMessage>(inMessages);
        messages.addFirst(SystemMessage.from(context.process(PROMT)));
        
        return chatModel.chat(request
                .temperature(0.2)
                .messages(toOneSystemMessage(messages))
                .build());
    }
    
    @Override
    public void withTools(List<ToolSpecification> tools) {
        request.toolSpecifications(tools);
    }
}