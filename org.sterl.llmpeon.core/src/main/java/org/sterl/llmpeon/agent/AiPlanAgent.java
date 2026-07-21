package org.sterl.llmpeon.agent;

import java.util.function.Predicate;

import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.prompt.PromptLoader;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;

public class AiPlanAgent extends AbstractAgent {

    public static final String NAME = "Peon-Plan";
    private static final String BASE_PROMPT = PromptLoader.loadWithDefault("planner.txt");

    public AiPlanAgent(ConfiguredChatModel configuredModel, ToolService toolService) {
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
    public org.sterl.llmpeon.ai.AgentConfig getConfig() {
        return configuredModel.getConfig().planAgentConfig();
    }

    @Override
    public boolean isThinkEnabled() {
        return configuredModel.getConfig().isPlanThinkEnabled();
    }

    @Override
    public String getAgentModelName() {
        return configuredModel.getConfig().getPlanModel();
    }

    @Override
    public String handoverTo() {
        return AiDevAgent.NAME;
    }

    /**
     * @return <code>true</code> if changed, <code>false</code> if already set
     */
    public boolean setModelName(AiModel modelName) {
        var cfg = configuredModel.getConfig();
        if (modelName == null || modelName.getId() == null) {
            if (cfg.getPlanModel() == null) return false;
            this.configuredModel.updateConfig(cfg.toBuilder().planModel(null).build());
            return true;
        }
        if (!modelName.getId().equals(cfg.getPlanModel())) {
            this.configuredModel.updateConfig(cfg.toBuilder().planModel(modelName.getId()).build());
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
