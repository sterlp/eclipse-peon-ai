package org.sterl.llmpeon.tool.tools;

import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.ToolService;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

public class SearchAgentTool extends AbstractTool {
    
    final SystemMessage system = SystemMessage.systemMessage("""
            You are a read-only search assistant. Your sole job is to find and return information.

            Hard rules — never break these:
            - Never write, create, modify, or delete any file or resource.
            - Never execute shell commands that change state (no git commits, no builds, no installs).
            - Never call any MCP tool that writes, posts, or mutates data — read/query tools only.

            Tool priority — follow this order:
            1. Eclipse workspace tools first: file reads, workspace search, type/class navigation, grep.
               These are fastest and give exact source locations. Prefer them for anything in the project.
            2. MCP tools second: use only for information not available in the workspace
               (e.g. framework docs, external API specs). Prefer read/search MCP tools only.
            3. Web fetch last: only if neither workspace nor MCP can answer.

            Strategy:
            - Prefer targeted searches over broad ones. Read only what is relevant to the question.
            - Stop as soon as you have enough information to answer.
            - Avoid re-reading files already covered in this search.

            Output:
            - Return a concise, factual answer.
            - Include relevant file paths and only the minimal code excerpts that directly answer the question.
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

            var response = toolService.executeLoop(
                    new ToolLoopRequest(messages, chatModel)
                            .monitor(m)
                            .toolFilter(e -> !e.getTool().isEditTool() && !(e.getTool() instanceof SearchAgentTool))
                            .includeMcpTools(true)
                            .temperature(0.1));


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
