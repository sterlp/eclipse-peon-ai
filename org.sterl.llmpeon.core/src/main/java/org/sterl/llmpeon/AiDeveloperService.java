package org.sterl.llmpeon;

import java.util.function.Predicate;

import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.template.TemplateContext;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;
import org.sterl.llmpeon.tool.tools.CompressorAgentTool;

public class AiDeveloperService extends AbstractChatService {

    private static final String BASE_PROMPT = """
            Embedded in Eclipse IDE. Today: ${currentDate}. Working in: ${workPath}.

            Tools:
            - Use tools automatically when applicable
            - Check currently selected file when no file context is given
            - Read project structure before creating files
            - Read matching SKILLs before starting a task
            - Compile and run tests to verify work
            - Avoid repeated tool calls for the same information

            Rules:
            - Never assume anything not confirmed by tool output or developer input
            - For complex tasks, create a plan and get developer approval before proceeding
            - If a needed tool doesn't exist, describe what the developer should implement
            - Ask when file placement or intent is ambiguous
            - Remove every word that does not add meaning to keep the context small
            - Call tools using JSON
            """;

    private static final String AGENT_MODE_ADDITION = """

            AGENT MODE — if you cannot proceed after 2 attempts (build failure, missing context,
            conflicting requirements), call reportProblem with a detailed description.
            Do not retry indefinitely. Escalate early so the plan agent can revise the plan.
            """;

    private final String prompt;

    public AiDeveloperService(LlmConfig config, ToolService toolService,
            SkillService skillService, TemplateContext templateContext) {
        this(config, toolService, skillService, templateContext, false);
    }

    public AiDeveloperService(LlmConfig config, ToolService toolService,
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
        return 0.3;
    }

    @Override
    protected Predicate<SmartToolExecutor> getToolFilter() {
        return t -> {
            if (t.getTool() instanceof CompressorAgentTool) {
                return getTokenSize() > getConfig().tokenWindow() * 0.7
                        && getMessages().size() > 5;
            }
            return true;
        };
    }
}
