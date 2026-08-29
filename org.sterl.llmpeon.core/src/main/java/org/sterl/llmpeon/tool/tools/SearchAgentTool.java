package org.sterl.llmpeon.tool.tools;

import java.util.Arrays;
import java.util.function.Predicate;

import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.prompt.PromptLoader;
import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.Getter;
import lombok.Setter;

public class SearchAgentTool extends AbstractTool {

    private final SystemMessage system = SystemMessage.systemMessage(PromptLoader.load("search-agent.txt"));

    @Getter @Setter
    private Predicate<SmartToolExecutor> filter = e -> !e.getTool().isEditTool() 
            && !(e.getTool() instanceof SearchAgentTool)
            && !(e.getTool() instanceof ShellTool);

    private final ToolService toolService;

    public SearchAgentTool(ToolService toolService) {
        this.toolService = toolService;
    }

    @Tool(name = "searchAgent", value = "Sub-agent for complex multi-step search/research - to save tokens.")
    public String searchAgent(@P(name = "prompt") String prompt) {
        ArgsUtil.requireNonBlank(prompt, "prompt");

        try {
            var messages = new ThreadSafeMemory();
            messages.add(UserMessage.from(prompt));

            var cfg = this.request.getConfig();
            var modelName = cfg.modelConfigFor(AgentModelConfig.SEARCH).model();

            var request = this.request.toBuilder()
                .staticMessages(Arrays.asList(system))
                .toolFilter(filter)
                .memory(messages)
                .agentConfig(cfg.searchAgentConfig());

            onTool("Da Sniffa "
                    + (modelName == null ? "" : "(" + modelName + ")")
                    + " start:" + System.lineSeparator() + System.lineSeparator() + prompt);
            long startNanos = System.nanoTime();
            dev.langchain4j.model.chat.response.ChatResponse response;
            response = toolService.executeLoop(request.build());
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

            onTool("Da Sniffa done. (" + StringUtil.humanElapsed(elapsedMillis) + ")");
            String answer = response != null ? response.aiMessage().text() : null;
            return StringUtil.hasValue(answer) ? answer : "Search completed but returned no result";

        } catch (Exception e) {
            onProblem("SearchAgent error: " + e.getMessage());
            return "Search agent failed check problem and hint user:\n" + StringUtil.getStackTrace(e);
        }
    }
}
