package org.sterl.llmpeon.tool;

import java.util.ArrayList;
import java.util.List;

import org.sterl.llmpeon.agent.AiMonitor;
import org.sterl.llmpeon.agent.AiSearchAgent;
import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

public class SearchAgentTool extends AbstractTool {

    private static final int MAX_ITERATIONS = 50;
    private final ToolService toolService;

    public SearchAgentTool(ToolService toolService) {
        this.toolService = toolService;
    }

    @Tool(name = "SearchAgentTool", value = "Sub-agent for complex multi-step search/research.")
    public String searchAgent(@P("search prompt") String prompt) {
        if (prompt == null || prompt.isBlank()) return "Error: search prompt must not be empty";

        final AiMonitor m = AiMonitor.nullSafety(monitor);

        try {
            var searchAgent = new AiSearchAgent(chatModel);
            searchAgent.withTools(toolService.readOnlyToolSpecifications());

            List<ChatMessage> messages = new ArrayList<>();
            messages.add(UserMessage.from(prompt));

            ChatResponse response = null;
            int iterations = 0;

            do {
                if (iterations++ >= MAX_ITERATIONS) {
                    m.onAction("SearchAgent: max iterations reached of " + MAX_ITERATIONS);
                    break;
                }
                response = searchAgent.call(messages, m);
                messages.add(response.aiMessage());
                
                messages = toolService.runAllTools(response, chatModel, m, messages);
                
                if (m.isCanceled()) break;
            } while (response.aiMessage().hasToolExecutionRequests()
                        || StringUtil.hasNoValue(response.aiMessage().text()));

            m.onAction("SearchAgent done (" + iterations + " iterations) for: " + prompt);
            String answer = response != null ? response.aiMessage().text() : null;
            return StringUtil.hasValue(answer) ? answer
                    : "Search completed but returned no result after " + iterations + " iterations.";

        } catch (Exception e) {
            String msg = "SearchAgent error: " + e.getMessage();
            onProblem(msg);
            return msg;
        }
    }
}
