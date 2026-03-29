package org.sterl.llmpeon;

import java.util.Optional;
import java.util.function.Predicate;

import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.data.message.AiMessage;
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
            - Think step by step before calling tools: goal -> scope -> exact files
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

    public AiPlannerService(LlmConfig config, ToolService toolService,
            SkillService skillService, TemplateContext templateContext) {
        super(config, toolService, skillService, templateContext);
    }

    @Override
    protected String getSystemPrompt() {
        return templateContext.process(BASE_PROMPT);
    }

    @Override
    protected double getTemperature() {
        return 0.8;
    }

    @Override
    protected Predicate<SmartToolExecutor> getToolFilter() {
        return t -> !t.getTool().isEditTool();
    }

    @Override
    protected boolean includesMcpTools() {
        return false;
    }

    /**
     * Returns the last AI message if the context is large enough to warrant
     * passing only the plan summary to the developer (rather than the full history).
     * Used during PLAN → DEV handoff.
     */
    public Optional<AiMessage> extractLastPlan() {
        boolean tooLarge = getMessages().size() > 4
                && getTokenSize() > (getTokenWindow() * 0.4);
        if (!tooLarge) return Optional.empty();
        var messages = getMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof AiMessage ai && StringUtil.hasValue(ai.text())) {
                return Optional.of(ai);
            }
        }
        return Optional.empty();
    }
}
