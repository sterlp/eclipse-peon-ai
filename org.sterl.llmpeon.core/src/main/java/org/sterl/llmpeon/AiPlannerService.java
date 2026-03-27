package org.sterl.llmpeon;

import java.util.function.Predicate;

import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.template.TemplateContext;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;

public class AiPlannerService extends AbstractChatService {

    private static final String BASE_PROMPT = """
            Embedded in Eclipse IDE. Today: ${currentDate}. Working in: ${workPath}.
            Read-only agent — no file modifications, no shell edit commands, no state changes.

            Tools:
            - Use read/search tools to explore the codebase (file reads, workspace search, grep, code navigation, web fetch)
            - Read matching SKILLs before starting
            - Avoid repeated tool calls for the same information

            Rules:
            - Ask clarifying questions before proceeding — never assume
            - Explore structure iteratively: goal -> affected area -> constraints -> exact scope
            - Identify exact files, classes, and interfaces affected
            - Use the Eclipse workspace tools to read files and project structure
            - Use SearchAgentTool for initial discovery to minimize context size
            - preserve the paths in the plan, to avoid searches during the implementation phase
            - Avoid reading files you already read in the chat history
            - Remove every word that does not add meaning to keep the context small
            - Call tools using JSON

            Final output (mandatory last message):
            A complete, self-contained implementation plan structured as:
            1. Context
            2. Affected files
            3. Step-by-step changes
            4. Open questions (if any)

            The plan is the sole input to the implementation agent — omit nothing it will need.
            """;

    private static final String AGENT_MODE_ADDITION = """

            AGENT MODE — when your plan is complete, call savePlan with the full plan markdown.
            Do not ask — call it automatically as the last action before your reply.
            The plan file path is also available for incremental updates via disk tools.
            If a problem was reported in the conversation, update the plan to address it before saving.
            """;

    private final String prompt;

    public AiPlannerService(LlmConfig config, ToolService toolService,
            SkillService skillService, TemplateContext templateContext) {
        this(config, toolService, skillService, templateContext, false);
    }

    public AiPlannerService(LlmConfig config, ToolService toolService,
            SkillService skillService, TemplateContext templateContext, boolean agentMode) {
        super(config, toolService, skillService, templateContext);
        this.prompt = agentMode ? BASE_PROMPT + AGENT_MODE_ADDITION : BASE_PROMPT;
    }

    @Override
    protected String getSystemPrompt() {
        return templateContext.process(prompt);
    }

    @Override
    protected double getTemperature() {
        return 0.8;
    }

    @Override
    protected Predicate<SmartToolExecutor> getToolFilter() {
        return t -> !t.getTool().isEditTool();
    }
}
