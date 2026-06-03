package org.sterl.llmpeon.tool.tools;

import org.sterl.llmpeon.PromptLoader;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.streaming.StreamingBridge;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.ToolService;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

public class SearchAgentTool extends AbstractTool {

    final SystemMessage system = SystemMessage.systemMessage(PromptLoader.loadWithDefault("search-agent.txt"));

    private final ToolService toolService;

    public SearchAgentTool(ToolService toolService) {
        this.toolService = toolService;
    }

    @Tool(name = "SearchAgentTool", value = "Sub-agent for complex multi-step search/research - to save tokens.")
    public String searchAgent(@P(name = "prompt") String prompt) {
        ArgsUtil.requireNonBlank(prompt, "prompt");
        final AiMonitor m = AiMonitor.nullSafety(monitor);

        try {
            var messages = MessageWindowChatMemory.withMaxMessages(5000);
            messages.add(system);
            messages.add(UserMessage.from(prompt));

            onTool("SearchAgent start: " + prompt);
            var response = toolService.executeLoop(
                    new ToolLoopRequest(messages, chatModel, new StreamingBridge())
                            .monitor(m)
                            .toolFilter(e -> !e.getTool().isEditTool() && !(e.getTool() instanceof SearchAgentTool))
                            .includeMcpTools(true));

            onTool("SearchAgent done for: " + prompt);
            String answer = response != null ? response.aiMessage().text() : null;
            return StringUtil.hasValue(answer) ? answer : "Search completed but returned no result";

        } catch (Exception e) {
            String msg = "SearchAgent error: " + e.getMessage();
            onProblem(msg);
            return msg;
        }
    }
}
