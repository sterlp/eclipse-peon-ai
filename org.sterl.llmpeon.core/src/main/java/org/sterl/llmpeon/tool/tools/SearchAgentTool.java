package org.sterl.llmpeon.tool.tools;

import org.sterl.llmpeon.PromptLoader;
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
    
    final SystemMessage system = SystemMessage.systemMessage(PromptLoader.load("search-agent.txt"));

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
