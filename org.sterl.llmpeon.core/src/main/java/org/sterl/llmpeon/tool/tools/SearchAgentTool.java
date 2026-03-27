package org.sterl.llmpeon.tool.tools;

import java.util.Collections;

import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.ToolService;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

public class SearchAgentTool extends AbstractTool {
    
    final SystemMessage system = SystemMessage.systemMessage("""
            You are a focused search assistant. Your only job is to find information.

            Strategy:
            - Use available tools to explore files, read source code, fetch documentation, or navigate types.
            - Prefer targeted searches over broad ones. Read only what is relevant to the question.
            - Stop using tools as soon as you have enough information to answer.
            - Remove every word that does not add meaning to keep the context small
            - Try to read informations from the eclipse workspace class search first before reaching out to the web or mcp

            Output:
            - Return a concise, factual answer.
            - Include the relevant file paths and only the minimal code excerpts that directly answer the question.
            - Omit irrelevant detail.
            - Do not ask follow-up questions. If you cannot find the answer, say so and explain what you tried.
            """);

    private final ToolService toolService;

    public SearchAgentTool(ToolService toolService) {
        this.toolService = toolService;
    }

    @Tool(name = "SearchAgentTool", value = "Sub-agent for complex multi-step search/research.")
    public String searchAgent(@P("search prompt") String prompt) {
        if (prompt == null || prompt.isBlank()) return "Error: search prompt must not be empty";

        final AiMonitor m = AiMonitor.nullSafety(monitor);

        try {
            ChatMemory messages = MessageWindowChatMemory
                    .withMaxMessages(ToolService.MAX_ITERATIONS + 10);
            messages.add(system);
            messages.add(UserMessage.from(prompt));

            var response = toolService.executeLoop(Collections.emptyList(), messages, 
                    chatModel, m, 
                    e -> !e.getTool().isEditTool() && !(e.getTool() instanceof SearchAgentTool), 
                    0.1);


            onTool("SearchAgent done for: " + prompt);
            String answer = response != null ? response.aiMessage().text() : null;
            return StringUtil.hasValue(answer) ? answer
                    : "Search completed but returned no result after " + ToolService.MAX_ITERATIONS + " iterations.";

        } catch (Exception e) {
            String msg = "SearchAgent error: " + e.getMessage();
            onProblem(msg);
            return msg;
        }
    }
}
