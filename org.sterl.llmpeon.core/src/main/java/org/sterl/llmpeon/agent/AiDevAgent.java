package org.sterl.llmpeon.agent;

import java.nio.file.Path;

import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.memory.FileAgentHistoryStore;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.prompt.PromptLoader;
import org.sterl.llmpeon.tool.ToolService;

public class AiDevAgent extends AbstractAgent {

    public static final String NAME = "Peon-Dev";
    private static final String BASE_PROMPT = PromptLoader.loadWithDefault("developer.md");

    public AiDevAgent(ConfiguredChatModel configuredModel,
            ToolService toolService) {
        super(configuredModel, toolService);
    }

    public AiDevAgent(ConfiguredChatModel configuredModel,
            ToolService toolService,
            Path historyStateDir) {
        super(configuredModel, toolService,
                historyStateDir == null ? new ThreadSafeMemory() : new ThreadSafeMemory(new FileAgentHistoryStore(historyFile(historyStateDir, NAME))));
    }

    @Override
    public String getSystemPrompt() {
        return BASE_PROMPT;
    }

    @Override
    protected String toolAllowlistSlot() {
        return AgentModelConfig.DEV;
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
