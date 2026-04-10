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

    private static final String BASE_PROMPT = PromptLoader.loadWithDefault("planner.txt");

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
     * Returns the last AI message from the planner conversation.
     * Used during PLAN → DEV handoff to pass the plan to the developer service.
     */
    public Optional<AiMessage> extractLastPlan() {
        var messages = getMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof AiMessage ai && StringUtil.hasValue(ai.text())) {
                return Optional.of(ai);
            }
        }
        return Optional.empty();
    }
}
