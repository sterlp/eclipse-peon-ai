package org.sterl.llmpeon;

import java.util.function.Predicate;

import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.template.TemplateContext;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;
import org.sterl.llmpeon.tool.tools.CompressorAgentTool;

public class AiDeveloperService extends AbstractChatService {

    private static final String BASE_PROMPT = PromptLoader.loadWithDefault("developer.txt");

    public AiDeveloperService(LlmConfig config, ToolService toolService,
            SkillService skillService, TemplateContext templateContext) {
        super(config, toolService, skillService, templateContext);
    }

    @Override
    protected String getSystemPrompt() {
        return templateContext.process(BASE_PROMPT);
    }

    @Override
    protected double getTemperature() {
        return 0.3;
    }

    @Override
    protected Predicate<SmartToolExecutor> getToolFilter() {
        return t -> {
            if (t.getTool() instanceof CompressorAgentTool) {
                return getTokenSize() > getConfig().getTokenWindow() * 0.7
                        && getMessages().size() > 5;
            }
            return true;
        };
    }
}
