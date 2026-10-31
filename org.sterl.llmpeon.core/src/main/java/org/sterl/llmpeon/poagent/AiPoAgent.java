package org.sterl.llmpeon.poagent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.sterl.llmpeon.agent.AbstractAgent;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.ai.AgentConfig;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.ThinkResolver;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.memory.FileAgentHistoryStore;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.poagent.tools.PoDelegateTool;
import org.sterl.llmpeon.prompt.PeonPaths;
import org.sterl.llmpeon.prompt.PromptLoader;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.WriteValidator;

/**
 * Jon — a docs-owning agent. Reads freely, writes only under docs/ (via
 * {@link WriteValidator#DOCS}). Unlike {@link AiPlanAgent} he keeps the edit tools (the validator, not
 * a tool filter, scopes him). Uses his own {@link AgentConfig} for provider/model/think.
 */
public class AiPoAgent extends AbstractAgent {
    
    public static final String AGENT_MODE = """
            You are in Agent Mode and report to Jon.
            You have someone to ask — use that. Save your state to the
            plan/task file first, then ask him directly, with more
            questions rather than fewer. Never assume.
            """;

    public static final String NAME = "Jon";
    // ${docs}/${plan} placeholders resolved from PeonPaths so the paths live in one constant, not the prompt.
    private static final String BASE_PROMPT = PeonPaths.resolve(PromptLoader.loadWithDefault("po.md"));
    private static final String DOCS_PROMPT = PeonPaths.resolve(PromptLoader.loadWithDefault("doc-method.md"));
    // Delegation playbook appended after Jon's identity/methodology prompt — kept out of po.txt so his
    // identity stays clean. Steers the talkPlan/planWithPlanAgent/askDev/buildWithDev loop: plan →
    // sign-off → build → mandatory post-build review, then planImplemented as the closing step.
    private static final String DELEGATION_PROMPT = PeonPaths.resolve(PromptLoader.load("po-delegation.md"));

    /** Jon's delegation partners, shown in the header; empty when he runs without delegation (tests). */
    private final List<AiAgent> slaves;

    public AiPoAgent(ConfiguredChatModel configuredModel, ToolService toolService) {
        super(configuredModel, toolService);
        this.slaves = List.of();
    }

    public AiPoAgent(ConfiguredChatModel configuredModel, ToolService toolService, Path historyStateDir) {
        this(configuredModel, toolService, historyStateDir, List.of());
    }

    public AiPoAgent(ConfiguredChatModel configuredModel, ToolService toolService, Path historyStateDir,
            List<AiAgent> slaves) {
        super(configuredModel, toolService,
                historyStateDir == null ? new ThreadSafeMemory()
                        : new ThreadSafeMemory(new FileAgentHistoryStore(historyFile(historyStateDir, NAME))));
        this.slaves = List.copyOf(slaves);
    }

    /**
     * The one writer of a slave's identity line. {@code context} is <b>shared</b> content: it is copied
     * verbatim into every slave (Replace-Semantik, fan-out rule R-N1 in
     * org.sterl.llmpeon.core/docs/sklaven-kontext-plan.md), so it must never contain an identity — a name
     * in here speaks out of every member at once. Each member gets exactly one identity item of its own
     * here ({@link AiAgent#getName()} plus {@link #AGENT_MODE}); an agent's own identity stays in its prompt.
     */
    @Override
    public void setStaticContext(List<ContextItem> context) {
        super.setStaticContext(context);
        this.slaves.forEach(s -> {
            s.setStaticContext(ContextItem.newList(context,
                    ContextItem.of("Your name is " + s.getName()
                            + System.lineSeparator() + AGENT_MODE)));
        });
    }

    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Jon's visible team for the header status widget (ADR-0025): <b>Jon</b> himself first, then his
     * team members in lifecycle order <b>Plan</b>, <b>Dev</b> and <b>Review</b> (ADR-0079).
     * Always the same instances
     * {@code PoDelegateTool} drives, so the widget reads their live {@code isWorking()}/context.
     */
    public List<AiAgent> getTeam() {
        var team = new ArrayList<AiAgent>(this.slaves.size() + 1);
        team.add(this);
        team.addAll(this.slaves);
        return List.copyOf(team);
    }

    @Override
    public String getSystemPrompt() {
        String scope = getWriteValidator().pathScopePrompt();
        return BASE_PROMPT + System.lineSeparator()
            + DOCS_PROMPT + System.lineSeparator()
            + (scope.isEmpty() ? "" : System.lineSeparator() + scope + System.lineSeparator())
            + DELEGATION_PROMPT;
    }

    @Override
    public WriteValidator getWriteValidator() {
        return WriteValidator.DOCS;
    }

    @Override
    protected String toolAllowlistSlot() {
        return AgentModelConfig.PO;
    }

    @Override
    public AgentConfig getConfig() {
        return configuredModel.getConfig().poAgentConfig();
    }

    @Override
    public boolean isThinkSupported() {
        return !ThinkResolver.isOff(configuredModel.getConfig().modelConfigFor(AgentModelConfig.PO).think());
    }

    @Override
    public String getAgentModelName() {
        var cfg = configuredModel.getConfig();
        var po = cfg.modelConfigFor(AgentModelConfig.PO).model();
        return StringUtil.hasValue(po) ? po : cfg.getModel();
    }

    @Override
    public boolean setAgentModelName(String modelName) {
        var cfg = configuredModel.getConfig();
        var po = cfg.modelConfigFor(AgentModelConfig.PO);
        if (Objects.equals(modelName, po.model())) return false;
        this.configuredModel.updateConfig(cfg.withModelConfig(AgentModelConfig.PO, po.withModel(modelName)));
        return true;
    }
    
    @Override
    public void clear() {
        super.clear();
        toolService.getTool(PoDelegateTool.class).ifPresent(t -> {
            t.clearPlan();
            t.clearReview();
            t.clearDev();
        });
    }
}
