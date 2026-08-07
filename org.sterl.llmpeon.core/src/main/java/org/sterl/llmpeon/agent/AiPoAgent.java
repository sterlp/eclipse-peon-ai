package org.sterl.llmpeon.agent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.sterl.llmpeon.ai.AgentConfig;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.memory.FileAgentHistoryStore;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.prompt.PeonPaths;
import org.sterl.llmpeon.prompt.PromptLoader;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.WriteValidator;

/**
 * Peon-PO ("Jon") — a docs-owning agent. Reads freely, writes only under docs/ (via
 * {@link WriteValidator#DOCS}). Unlike {@link AiPlanAgent} he keeps the edit tools (the validator, not
 * a tool filter, scopes him). Reuses the plan {@link AgentConfig} for provider/think/temperature.
 */
public class AiPoAgent extends AbstractAgent {

    public static final String NAME = "Peon-PO";
    // ${docs}/${plan} placeholders resolved from PeonPaths so the paths live in one constant, not the prompt.
    private static final String BASE_PROMPT = PeonPaths.resolve(PromptLoader.loadWithDefault("po.txt"));
    // Delegation playbook appended after Jon's identity/methodology prompt — kept out of po.txt so his
    // identity stays clean. Steers the talkPlan/planWithPlanAgent/askDev/buildWithAgent loop: plan →
    // sign-off → build → mandatory post-build review, then planImplemented as the closing step.
    private static final String DELEGATION_PROMPT = PeonPaths.resolve(PromptLoader.load("po-delegation.txt"));

    /** Jon's own ork slaves, shown in the header. Empty when Jon runs without delegation (e.g. tests). */
    private final List<NamedAgent> slaves;

    public AiPoAgent(ConfiguredChatModel configuredModel, ToolService toolService) {
        super(configuredModel, toolService);
        this.slaves = List.of();
    }

    public AiPoAgent(ConfiguredChatModel configuredModel, ToolService toolService, Path historyConfigDir) {
        this(configuredModel, toolService, historyConfigDir, List.of());
    }

    public AiPoAgent(ConfiguredChatModel configuredModel, ToolService toolService, Path historyConfigDir,
            List<NamedAgent> slaves) {
        super(configuredModel, toolService,
                historyConfigDir == null ? new ThreadSafeMemory()
                        : new ThreadSafeMemory(new FileAgentHistoryStore(historyFile(historyConfigDir, NAME))));
        this.slaves = List.copyOf(slaves);
    }

    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Jon's visible team for the header status widget (ADR-0025): <b>Da Boss</b> (Jon himself) first,
     * then his ork slaves <b>Da Thinka</b> (Plan) and <b>Da Mek</b> (Dev). Always the same instances
     * {@code JonDelegateTool} drives, so the widget reads their live {@code isWorking()}/context.
     */
    public List<NamedAgent> getTeam() {
        var team = new ArrayList<NamedAgent>(slaves.size() + 1);
        team.add(new NamedAgent("Da Boss", this));
        team.addAll(slaves);
        return List.copyOf(team);
    }

    @Override
    public String getSystemPrompt() {
        return BASE_PROMPT + System.lineSeparator()
            + System.lineSeparator() + "- Your path white list " + WriteValidator.DEFAULT_ALLOW
            + System.lineSeparator() + DELEGATION_PROMPT;
    }

    @Override
    public WriteValidator getWriteValidator() {
        return WriteValidator.DOCS;
    }

    @Override
    public Double getTemperature() {
        return configuredModel.getConfig().getPlanTemperature();
    }

    @Override
    public AgentConfig getConfig() {
        var cfg = configuredModel.getConfig();
        var plan = cfg.planAgentConfig();
        // Jon uses the plan model slot; when it is unset he falls back to the dev/default model.
        return StringUtil.hasValue(plan.getModel())
                ? plan
                : plan.toBuilder().model(cfg.getModel()).build();
    }

    @Override
    public boolean isThinkSupported() {
        return configuredModel.getConfig().isPlanThinkSupported();
    }

    @Override
    public String getAgentModelName() {
        var cfg = configuredModel.getConfig();
        return StringUtil.hasValue(cfg.getPlanModel()) ? cfg.getPlanModel() : cfg.getModel();
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
}
