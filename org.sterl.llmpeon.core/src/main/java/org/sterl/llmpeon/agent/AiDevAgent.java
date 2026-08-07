package org.sterl.llmpeon.agent;

import java.nio.file.Path;

import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.memory.FileAgentHistoryStore;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.prompt.PromptLoader;
import org.sterl.llmpeon.tool.ToolService;

public class AiDevAgent extends AbstractAgent {

    public static final String NAME = "Peon-Dev";
    private static final String BASE_PROMPT = PromptLoader.loadWithDefault("developer.txt");

    public AiDevAgent(ConfiguredChatModel configuredModel,
            ToolService toolService) {
        super(configuredModel, toolService);
    }

    /** RAM-only Dev slave with an earlier compaction budget (see {@link AbstractAgent#compactAfterTokens()}). */
    public AiDevAgent(ConfiguredChatModel configuredModel, ToolService toolService, double compactFactor) {
        super(configuredModel, toolService, new ThreadSafeMemory(), compactFactor);
    }

    public AiDevAgent(ConfiguredChatModel configuredModel,
            ToolService toolService,
            Path historyConfigDir) {
        super(configuredModel, toolService,
                historyConfigDir == null ? new ThreadSafeMemory() : new ThreadSafeMemory(new FileAgentHistoryStore(historyFile(historyConfigDir, NAME))));
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
