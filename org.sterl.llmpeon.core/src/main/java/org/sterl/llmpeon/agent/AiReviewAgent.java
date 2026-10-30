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
import org.sterl.llmpeon.tool.WriteValidator;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;
import org.sterl.llmpeon.tool.tools.ShellTool;

/**
 * Own "agent" for a review with a system custom prompt and its own model slot (REVIEW, R7)
 */
public class AiReviewAgent extends AbstractAgent {

    public static final String NAME = "Review";
    private static final String BASE_PROMPT = PromptLoader.loadWithDefault("review-agent.md");

    public AiReviewAgent(ConfiguredChatModel configuredModel, ToolService toolService) {
        super(configuredModel, toolService);
    }

    public AiReviewAgent(ConfiguredChatModel configuredModel, ToolService toolService, Path historyStateDir) {
        super(configuredModel, toolService,
                historyStateDir == null ? new ThreadSafeMemory() : new ThreadSafeMemory(new FileAgentHistoryStore(historyFile(historyStateDir, NAME))));
    }

    @Override
    public String getSystemPrompt() {
        return BASE_PROMPT;
    }

    @Override
    public org.sterl.llmpeon.ai.AgentConfig getConfig() {
        return configuredModel.getConfig().reviewAgentConfig();
    }

    @Override
    public boolean isThinkSupported() {
        return !ThinkResolver.isOff(configuredModel.getConfig().modelConfigFor(AgentModelConfig.REVIEW).think());
    }

    @Override
    public String getAgentModelName() {
        return configuredModel.getConfig().modelConfigFor(AgentModelConfig.REVIEW).model();
    }

    @Override
    public String handoverTo() {
        return AiPlanAgent.NAME;
    }
    
    @Override
    public boolean setAgentModelName(String modelName) {
        var cfg = configuredModel.getConfig();
        var review = cfg.modelConfigFor(AgentModelConfig.REVIEW);
        if (Objects.equals(modelName, review.model())) return false;
        this.configuredModel.updateConfig(cfg.withModelConfig(AgentModelConfig.REVIEW, review.withModel(modelName)));
        return true;
    }

    @Override
    protected String toolAllowlistSlot() {
        return AgentModelConfig.REVIEW;
    }

    @Override
    protected Predicate<SmartToolExecutor> classFilter() {
        // R-TF-1: Shell is Review's only whitelist exception — review diagnosis needs git/build/test runs.
        return super.classFilter().and(t -> !t.getTool().isEditTool() || t.getTool() instanceof ShellTool);
    }

    @Override
    public WriteValidator getWriteValidator() {
        return WriteValidator.DENY_ALL;
    }

    @Override
    public String getName() {
        return NAME;
    }
}
