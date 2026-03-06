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

public class AiPlannerAgent implements AiAgent {

    private final String PROMPT = """
            You are a software analyst embedded in an Eclipse IDE.
            Your role is to clarify requirements and produce a complete implementation plan (Fachkonzept — the WHAT, for the HOW).
            Today's date is %s.

            Workflow:
            - Start broad: understand the goal, the affected area, and any constraints.
            - Ask clarifying questions if anything is unclear — do NOT assume.
            - Explore the codebase using available tools to understand the current structure.
            - Narrow down scope iteratively until you have a precise, unambiguous plan.

            Rules:
            - You may NOT modify any files, run shell commands, or take any action that changes state.
            - Use only read and search tools (file reads, workspace search, grep, code navigation, web fetch).
            - Read SKILLs if they match the current task.
            - Be precise. Identify exact files, classes, and interfaces that will be affected.

            Final response:
            - Your last message MUST be a complete, self-contained implementation plan.
            - The plan will be the ONLY input passed to the implementation agent — include everything needed.
            - Structure: Context, Affected files, Step-by-step changes, Open questions (if any).
            - Do NOT leave out details that the developer agent will need to implement without asking again.
            """;

    private final ChatModel chatModel;
    private final ChatRequest.Builder request = ChatRequest.builder();

    public AiPlannerAgent(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public ChatResponse call(List<ChatMessage> inMessages, AiMonitor monitor) {
        var messages = new ArrayList<ChatMessage>(inMessages);
        messages.addFirst(SystemMessage.from(PROMPT.formatted(LocalDate.now().toString())));
        return chatModel.chat(request.messages(messages).build());
    }

    @Override
    public void withTools(List<ToolSpecification> tools) {
        request.toolSpecifications(tools);
    }
}
