package org.sterl.llmpeon.agent;

import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.prompt.PromptLoader;
import org.sterl.llmpeon.tool.ToolService;

public class AiDevAgent extends AbstractAgent {

    public static final String NAME = "Peon-Dev";
    private static final String BASE_PROMPT = PromptLoader.loadWithDefault("developer.txt");

    public AiDevAgent(ConfiguredChatModel configuredModel,
            ToolService toolService) {
        super(configuredModel, toolService);
    }

    @Override
    public String getSystemPrompt() {
        return BASE_PROMPT;
    }

    @Override
    public Double getTemperature() {
        return configuredModel.getConfig().getDevTemperature();
    }

    @Override
    public String getAgentModelName() {
        return configuredModel.getConfig().getModel();
    }
    
    @Override
    public boolean setAgentModelName(String modelName) {
        return this.configuredModel.withModel(modelName);
    }

    @Override
    public String getName() {
        return NAME;
    }
}
