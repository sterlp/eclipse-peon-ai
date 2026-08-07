package org.sterl.llmpeon.agent;

import java.nio.file.Path;
import java.util.function.Predicate;

import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.memory.FileAgentHistoryStore;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.prompt.PromptLoader;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;

public class AiPlanAgent extends AbstractAgent {

    public static final String NAME = "Peon-Plan";
    private static final String BASE_PROMPT = PromptLoader.loadWithDefault("planner.txt");

    public AiPlanAgent(ConfiguredChatModel configuredModel, ToolService toolService) {
        super(configuredModel, toolService);
    }

    /** RAM-only Plan slave with an earlier compaction budget (see {@link AbstractAgent#compactAfterTokens()}). */
    public AiPlanAgent(ConfiguredChatModel configuredModel, ToolService toolService, double compactFactor) {
        super(configuredModel, toolService, new ThreadSafeMemory(), compactFactor);
    }

    public AiPlanAgent(ConfiguredChatModel configuredModel, ToolService toolService, Path historyConfigDir) {
        super(configuredModel, toolService,
                historyConfigDir == null ? new ThreadSafeMemory() : new ThreadSafeMemory(new FileAgentHistoryStore(historyFile(historyConfigDir, NAME))));
    }

    @Override
    public String getSystemPrompt() {
        return BASE_PROMPT;
    }

    @Override
    public Double getTemperature() {
        return configuredModel.getConfig().getPlanTemperature();
    }

    @Override
    public org.sterl.llmpeon.ai.AgentConfig getConfig() {
        return configuredModel.getConfig().planAgentConfig();
    }

    @Override
    public boolean isThinkSupported() {
        return configuredModel.getConfig().isPlanThinkSupported();
    }

    @Override
    public String getAgentModelName() {
        return configuredModel.getConfig().getPlanModel();
    }

    @Override
    public String handoverTo() {
        return AiDevAgent.NAME;
    }
    
    @Override
    public boolean setAgentModelName(String modelName) {
        var cfg = configuredModel.getConfig();
        if (modelName == null) {
            if (cfg.getPlanModel() == null) return false;
            this.configuredModel.updateConfig(cfg.toBuilder().planModel(null).build());
            return true;
        }
        if (!modelName.equals(cfg.getPlanModel())) {
            this.configuredModel.updateConfig(cfg.toBuilder().planModel(modelName).build());
            return true;
        }
        return false;
    }

    @Override
    protected Predicate<SmartToolExecutor> getToolFilter() {
        return super.getToolFilter().and(t -> !t.getTool().isEditTool());
    }

    @Override
    public String getName() {
        return NAME;
    }
}
