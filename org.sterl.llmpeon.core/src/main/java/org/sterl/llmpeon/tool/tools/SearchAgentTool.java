package org.sterl.llmpeon.tool.tools;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.prompt.PromptLoader;
import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.ToolPolicy;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.Getter;
import lombok.Setter;

public class SearchAgentTool extends AbstractTool {

    private final SystemMessage system = SystemMessage.systemMessage(PromptLoader.load("search-agent.md"));

    @Getter @Setter
    private Predicate<SmartToolExecutor> filter = e -> !e.getTool().isEditTool() 
            && !(e.getTool() instanceof SearchAgentTool)
            && !(e.getTool() instanceof ShellTool)
            // R-CC-15: a sub-loop has no owning agent — compactSession would hit the parent
            && !(e.getTool() instanceof CompactSessionTool);

    private final ToolService toolService;

    public SearchAgentTool(ToolService toolService) {
        this.toolService = toolService;
    }

    /**
     * Preview (R-ATC-8): the sorted names of ALL tools (built-in + MCP) that would be active for the
     * GIVEN raw CSV — the same filter the sub-loop sends, so the preview can never drift from the
     * runtime. The CSV is the value the user typed, not necessarily the stored one.
     */
    public List<String> resolveActiveTools(String rawCsv) {
        return toolService.activeNames(filter, ToolPolicy.nameFilter(rawCsv));
    }

    @Tool(name = "searchAgent", value = "Sub-agent for complex multi-step search/research - to save tokens.")
    public String searchAgent(@P(name = "prompt") String prompt) {
        ArgsUtil.requireNonBlank(prompt, "prompt");

        try {
            var messages = new ThreadSafeMemory();
            messages.add(UserMessage.from(prompt));

            var cfg = this.request.getConfig();
            var modelName = cfg.modelConfigFor(AgentModelConfig.SEARCH).model();
            // The sub-loop is parent-independent: its allowlist comes from the SEARCH slot, not
            // from the parent agent (toBuilder would inherit the parent's toolNameFilter, R-CC-15).
            var allow = ToolPolicy.nameFilter(cfg.toolAllowlist(AgentModelConfig.SEARCH));

            var request = this.request.toBuilder()
                .staticMessages(Arrays.asList(system))
                .toolFilter(filter.and(exec -> allow.test(exec.getSpec().name())))
                .toolNameFilter(allow)
                .memory(messages)
                .agentConfig(cfg.searchAgentConfig())
                // R-CC-15: never inherit the parent agent — a sub-loop compactSession would
                // clear/re-seed the PARENT memory (toBuilder inherits every field, see ToolLoopRequest)
                .agent(null);

            onTool("Da Sniffa "
                    + (modelName == null ? "" : "(" + modelName + ")")
                    + " start:" + System.lineSeparator() + System.lineSeparator() + prompt);
            long startNanos = System.nanoTime();
            dev.langchain4j.model.chat.response.ChatResponse response;
            response = toolService.executeLoop(request.build());
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

            String answer = response != null ? response.aiMessage().text() : null;
            String result = StringUtil.hasValue(answer) ? answer : "Search completed but returned no result";
            onTool("Da Sniffa done. (" + StringUtil.humanElapsed(elapsedMillis) + ")" + " " + StringUtil.charsSuffix(result));
            return result;

        } catch (Exception e) {
            onProblem("SearchAgent error: " + e.getMessage());
            return "Search agent failed check problem and hint user:\n" + StringUtil.getStackTrace(e);
        }
    }
}
