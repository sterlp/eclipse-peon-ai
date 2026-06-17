package org.sterl.llmpeon;

import java.util.Optional;
import java.util.function.Predicate;

import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.prompt.PromptLoader;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;

import dev.langchain4j.data.message.AiMessage;

public class AiPlannerService extends AbstractChatService {

    private static final String BASE_PROMPT = PromptLoader.loadWithDefault("planner.txt");

    public AiPlannerService(ConfiguredChatModel configuredModel, ToolService toolService) {
        super(configuredModel, toolService);
    }

    @Override
    protected String getSystemPrompt() {
        return BASE_PROMPT;
    }

    @Override
    public double getTemperature() {
        return configuredModel.getConfig().getDevTemperature();
    }

    @Override
    public String getAgentModelName() {
        return configuredModel.getConfig().getPlanModel();
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
    protected boolean includesMcpTools() {
        return false;
    }

    /**
     * Returns the last AI message from the planner conversation.
     * Used during PLAN -> DEV handoff to pass the plan to the developer service.
     */
    public Optional<AiMessage> extractLastPlan() {
        var message = memory.getLastOf(AiMessage.class); // getMessages();
        return Optional.ofNullable(message);
    }
}
