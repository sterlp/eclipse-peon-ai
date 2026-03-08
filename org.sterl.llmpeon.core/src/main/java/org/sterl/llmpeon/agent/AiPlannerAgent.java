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

public class AiPlannerAgent implements AiAgent {

    private final String PROMPT = """
            Embedded in Eclipse IDE. Today: ${currentDate}. Working in: ${workPath}.
            Read-only agent — no file modifications, no shell edit commands, no state changes.

            Tools:
            - Use read/search tools to explore the codebase (file reads, workspace search, grep, code navigation, web fetch)
            - Read matching SKILLs before starting
            - Avoid repeated tool calls for the same information

            Rules:
            - Ask clarifying questions before proceeding — never assume
            - Explore structure iteratively: goal -> affected area -> constraints -> exact scope
            - Identify exact files, classes, and interfaces affected
            - Use SearchAgentTool for initial discovery to minimize context size
            - preserve the paths in the plan, to avoid searches during the implementation phase

            Final output (mandatory last message):
            A complete, self-contained implementation plan structured as:
            1. Context
            2. Affected files
            3. Step-by-step changes
            4. Open questions (if any)

            The plan is the sole input to the implementation agent — omit nothing it will need.
            """;

    private final ChatModel chatModel;
    private final TemplateContext context;
    private final ChatRequest.Builder request = ChatRequest.builder();

    public AiPlannerAgent(ChatModel chatModel, TemplateContext context) {
        this.chatModel = chatModel;
        this.context = context;
    }

    public ChatResponse call(List<ChatMessage> inMessages, AiMonitor monitor) {
        var messages = new ArrayList<ChatMessage>(inMessages);
        messages.addFirst(SystemMessage.from(context.process(PROMPT)));
        return chatModel.chat(request.messages(messages).build());
    }

    @Override
    public void withTools(List<ToolSpecification> tools) {
        request.toolSpecifications(tools);
    }
}
