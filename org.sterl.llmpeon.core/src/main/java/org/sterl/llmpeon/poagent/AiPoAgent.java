package org.sterl.llmpeon.poagent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.sterl.llmpeon.agent.AbstractAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.agent.NamedAgent;
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
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.WriteValidator;

import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Peon-PO ("Jon") — a docs-owning agent. Reads freely, writes only under docs/ (via
 * {@link WriteValidator#DOCS}). Unlike {@link AiPlanAgent} he keeps the edit tools (the validator, not
 * a tool filter, scopes him). Uses his own {@link AgentConfig} for provider/model/think.
 */
public class AiPoAgent extends AbstractAgent {

    public static final String NAME = "Peon-PO";
    // ${docs}/${plan} placeholders resolved from PeonPaths so the paths live in one constant, not the prompt.
    private static final String BASE_PROMPT = PeonPaths.resolve(PromptLoader.loadWithDefault("po.txt"));
    // Delegation playbook appended after Jon's identity/methodology prompt — kept out of po.txt so his
    // identity stays clean. Steers the talkPlan/planWithPlanAgent/askDev/buildWithDev loop: plan →
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

    // jon delegates also the static content to the slaves ...
    @Override
    public void setStaticContext(List<ContextItem> context) {
        super.setStaticContext(context);
        this.slaves.forEach(s -> s.agent().setStaticContext(context));
    }

    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Jon's visible slaves for the header status widget (ADR-0025): <b>Da Boss</b> (Jon himself) first,
     * then his ork slaves <b>Da Thinka</b> (Plan) and <b>Da Mek</b> (Dev). Always the same instances
     * {@code PoDelegateTool} drives, so the widget reads their live {@code isWorking()}/context.
     */
    public List<NamedAgent> getTeam() {
        var team = new ArrayList<NamedAgent>(this.slaves.size() + 1);
        team.add(new NamedAgent("Da Boss", this));
        team.addAll(this.slaves);
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
            t.clearDev();
            t.clearPlan();
        });
    }
    
    @Override
    public ChatResponse compact(AiMonitor monitor) {
        var result = super.compact(monitor);
        toolService.getTool(PoDelegateTool.class).ifPresent(t -> {
            t.compactDev();
            t.compactPlan();
        });
        return result;
    }
}
