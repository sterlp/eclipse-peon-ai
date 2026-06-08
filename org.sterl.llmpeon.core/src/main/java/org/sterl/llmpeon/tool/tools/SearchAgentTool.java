package org.sterl.llmpeon.tool.tools;

import java.util.Arrays;

import org.sterl.llmpeon.PromptLoader;
import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.shared.StringUtil;
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

        try {
            var messages = MessageWindowChatMemory.withMaxMessages(5000);
            messages.add(UserMessage.from(prompt));

            var devTemp = this.request.getModel().getConfig().getDevTemperature();
            onTool("SearchAgent start: " + prompt);
            var response = toolService.executeLoop(this.request.toBuilder()
                    .staticMessages(Arrays.asList(system))
                    .includeMcpTools(true)
                    .toolFilter(e -> !e.getTool().isEditTool() && !(e.getTool() instanceof SearchAgentTool))
                    .temperature(devTemp < 1 ? 0.2 : devTemp)
                    .memory(messages)
                    .build()
                );

            onTool("SearchAgent done for: " + prompt);
            String answer = response != null ? response.aiMessage().text() : null;
            return StringUtil.hasValue(answer) ? answer : "Search completed but returned no result";

        } catch (Exception e) {
            onProblem("SearchAgent error: " + e.getMessage());
            return "Search agent failed check problem and hint user:\n" + StringUtil.getStackTrace(e);
        }
    }
}
