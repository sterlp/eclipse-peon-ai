package org.sterl.llmpeon.agent;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Predicate;

import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.ThinkResolver;
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
    public org.sterl.llmpeon.ai.AgentConfig getConfig() {
        return configuredModel.getConfig().planAgentConfig();
    }

    @Override
    public boolean isThinkSupported() {
        return !ThinkResolver.isOff(configuredModel.getConfig().modelConfigFor(AgentModelConfig.PLAN).think());
    }

    @Override
    public String getAgentModelName() {
        return configuredModel.getConfig().modelConfigFor(AgentModelConfig.PLAN).model();
    }

    @Override
    public String handoverTo() {
        return AiDevAgent.NAME;
    }
    
    @Override
    public boolean setAgentModelName(String modelName) {
        var cfg = configuredModel.getConfig();
        var plan = cfg.modelConfigFor(AgentModelConfig.PLAN);
        if (Objects.equals(modelName, plan.model())) return false;
        this.configuredModel.updateConfig(cfg.withModelConfig(AgentModelConfig.PLAN, plan.withModel(modelName)));
        return true;
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
