package org.sterl.llmpeon.tool;

import java.util.ArrayList;
import java.util.List;

import org.sterl.llmpeon.agent.AgentService;
import org.sterl.llmpeon.agent.AiMonitor;
import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

public class SearchAgentTool extends AbstractTool {

    private static final int MAX_ITERATIONS = 25;
    private final AgentService agentService;
    private final ToolService toolService;

    public SearchAgentTool(AgentService agentService, ToolService toolService) {
        this.agentService = agentService;
        this.toolService = toolService;
    }

    @Tool(name = "SearchAgentTool", value = """
            Delegates a search or research task to a dedicated search sub-agent.
            Use when finding information requires multiple tool calls: exploring files,
            reading source code, fetching documentation URLs, or navigating types.
            Examples:
              - "find all classes implementing SmartTool"
              - "what does WebFetchTool do?"
              - "search the web for langchain4j MistralAiChatModel API"
            Returns a concise factual summary with relevant file paths and code excerpts.
            """)
    public String search(@P("Natural-language description of what to search for") String prompt) {
        if (prompt == null || prompt.isBlank()) return "Error: search prompt must not be empty";

        final AiMonitor m = AiMonitor.nullSafty(monitor);
        m.onAction("SearchAgent: " + prompt);

        try {
            var searchAgent = agentService.newSearchAgent();
            searchAgent.withTools(agentService.nonAgentToolSpecs(toolService));

            List<ChatMessage> messages = new ArrayList<>();
            messages.add(UserMessage.from(prompt));

            ChatResponse response = null;
            int iterations = 0;

            do {
                if (iterations++ >= MAX_ITERATIONS) {
                    m.onAction("SearchAgent: max iterations reached");
                    break;
                }
                response = searchAgent.call(messages, m);
                messages.add(response.aiMessage());

                if (response.aiMessage().hasToolExecutionRequests()) {
                    for (ToolExecutionRequest tr : response.aiMessage().toolExecutionRequests()) {
                        messages.add(ToolExecutionResultMessage.from(tr.id(), tr.name(), toolService.execute(tr, m)));
                    }
                }
            } while (!m.isCanceled()
                    && (response.aiMessage().hasToolExecutionRequests()
                        || StringUtil.hasNoValue(response.aiMessage().text())));

            m.onAction("SearchAgent done (" + iterations + " iterations)");
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
