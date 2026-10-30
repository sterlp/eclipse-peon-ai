package org.sterl.llmpeon.tool.tools;

import java.util.List;
import java.util.function.Predicate;

import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.prompt.PromptLoader;
import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.LoopOwner;
import org.sterl.llmpeon.tool.ToolPolicy;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.Getter;
import lombok.Setter;

public class SearchAgentTool extends AbstractTool {

    /** The sub-loop's own name — stamped as {@code origin} on its lines (ADR-0078) and the label of its start line. */
    public static final String LOOP_NAME = "Search";

    private final SystemMessage system = SystemMessage.systemMessage(PromptLoader.load("search-agent.md"));

    @Getter @Setter
    private Predicate<SmartToolExecutor> filter = e -> !e.getTool().isEditTool() 
            && !(e.getTool() instanceof SearchAgentTool)
            && !(e.getTool() instanceof ShellTool)
            // R-CC-15: a sub-loop has no owning agent — compactSession would hit the parent
            && !(e.getTool() instanceof CompactSessionTool);

    /**
     * The rolling hot block of this chat view — one tool instance per view (R-LSR-6). Rendered as a
     * snapshot <b>before</b> the sub-loop, so a search never sees its own question (R-LSR-5).
     */
    private final RollingSearchContext rolling = new RollingSearchContext();

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
        // the field is re-set per tool call — read it once, so a parallel call's cancel state can never
        // decide this search's append (B7)
        var monitor = this.monitor;

        try {
            var messages = new ThreadSafeMemory();
            messages.add(UserMessage.from(prompt));

            var cfg = this.request.getConfig();
            var modelName = cfg.modelConfigFor(AgentModelConfig.SEARCH).model();
            // The sub-loop is parent-independent: its allowlist comes from the SEARCH slot, not
            // from the parent agent (toBuilder would inherit the parent's toolNameFilter, R-CC-15).
            var allow = ToolPolicy.nameFilter(cfg.toolAllowlist(AgentModelConfig.SEARCH));

            var history = rolling.render();
            var staticMessages = history.isEmpty()
                    ? List.<ChatMessage>of(system)
                    : List.<ChatMessage>of(system, SystemMessage.from(history));

            var request = this.request.toBuilder()
                .staticMessages(staticMessages)
                .toolFilter(filter.and(exec -> allow.test(exec.getSpec().name())))
                .toolNameFilter(allow)
                .memory(messages)
                // R-CC-15: an owner of its own — no parent agent to delegate back to (a sub-loop
                // compactSession would clear/re-seed the PARENT memory, toBuilder inherits every field,
                // see ToolLoopRequest) — and its own name on every line it emits (ADR-0078).
                .owner(LoopOwner.named(LOOP_NAME, base -> base.searchAgentConfig()));

            onTool(LOOP_NAME + " "
                    + (modelName == null ? "" : "(" + modelName + ")")
                    + " start:" + System.lineSeparator() + System.lineSeparator() + prompt);
            long startNanos = System.nanoTime();
            dev.langchain4j.model.chat.response.ChatResponse response;
            response = toolService.executeLoop(request.build());
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

            String answer = response != null ? response.aiMessage().text() : null;
            // R-LSR-1/2: only a complete search earns a pair — a cancel leaves a partial answer, and
            // the "no result" fallback must never enter the block.
            if (response != null && StringUtil.hasValue(answer) && !monitor.isCanceled()) {
                rolling.append(prompt, answer);
            }
            String result = StringUtil.hasValue(answer) ? answer : "Search completed but returned no result";
            onTool(LOOP_NAME + " done. (" + StringUtil.humanElapsed(elapsedMillis) + ")" + " "
                    + StringUtil.charsSuffix(result));
            return result;

        } catch (Exception e) {
            onProblem("SearchAgent error: " + e.getMessage());
            return "Search agent failed check problem and hint user:\n" + StringUtil.getStackTrace(e);
        }
    }
}
