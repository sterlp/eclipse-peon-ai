package org.sterl.llmpeon;

import org.sterl.llmpeon.ai.ConfiguredModel;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.tool.ToolService;

public class AiDeveloperService extends AbstractChatService {

    private static final String BASE_PROMPT = PromptLoader.loadWithDefault("developer.txt");

    public AiDeveloperService(ConfiguredModel configuredModel,
            ToolService toolService,
            SkillService skillService) {
        super(configuredModel, toolService, skillService);
    }

    @Override
    protected String getSystemPrompt() {
        return BASE_PROMPT;
    }

    protected double getTemperature() {
        return configuredModel.getConfig().getDevTemperature();
    }
}
