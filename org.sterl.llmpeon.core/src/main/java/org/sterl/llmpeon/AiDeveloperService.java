package org.sterl.llmpeon;

import org.sterl.llmpeon.ai.ConfiguredModel;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.template.TemplateContext;
import org.sterl.llmpeon.tool.ToolService;

public class AiDeveloperService extends AbstractChatService {

    private static final String BASE_PROMPT = PromptLoader.loadWithDefault("developer.txt");

    public AiDeveloperService(ConfiguredModel configuredModel,
            ToolService toolService,
            SkillService skillService, TemplateContext templateContext) {
        super(configuredModel, toolService, skillService, templateContext);
    }

    @Override
    protected String getSystemPrompt() {
        return templateContext.process(BASE_PROMPT);
    }

    protected double getTemperature() {
        return configuredModel.getConfig().getDevTemperature();
    }
}
