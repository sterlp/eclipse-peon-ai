package org.sterl.open.aifactory.chat;

import java.util.List;

import org.springframework.stereotype.Service;

import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.agent.AbstractAgent;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.agent.AiReviewAgent;
import org.sterl.llmpeon.poagent.AiPoAgent;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.tools.SearchAgentTool;
import org.sterl.open.aifactory.config.AgentToolPreview;
import org.sterl.open.aifactory.config.FactoryConfigService;
import org.sterl.open.aifactory.shared.TmpRoots;

/**
 * The tool preview of R-WP-52, answering through the core's own preview source —
 * {@code AbstractAgent.resolveActiveTools} (and {@code SearchAgentTool}'s equivalent for the search
 * slot) — so the list can never drift from what a turn would send. The agent exists only for this
 * call: no model request leaves the process, the answer is computed from registered tool names.
 *
 * <p>The basis is the tool set {@link JonToolSet} builds for a project, on a temporary root: the
 * preview asks for <em>names</em>, never for write access, and a real checkout would hand a running
 * agent a write root nobody gave it. It includes Jon's delegation tool — {@code ProjectRuntime}
 * registers it on the same set before an agent ever starts, so a preview that omitted it would name
 * fewer tools than a turn sends (UC-WP-88 GIVEN 4).</p>
 */
@Service
public class ProjectToolPreview implements AgentToolPreview {

    private final FactoryConfigService config;

    public ProjectToolPreview(FactoryConfigService config) {
        this.config = config;
    }

    @Override
    public List<String> activeTools(String slotId, String rawAllowlist) {
        JonToolSet toolSet = JonToolSet.forProject(TmpRoots.systemTmp());
        ToolService tools = toolSet.tools();
        if (AgentModelConfig.SEARCH.equals(slotId)) {
            // the slot's restriction is the tool's own filter — not a second one invented here
            return tools.getTool(SearchAgentTool.class)
                    .map(search -> search.resolveActiveTools(rawAllowlist))
                    .orElseThrow(() -> new IllegalArgumentException("search tool is not registered"));
        }
        ConfiguredChatModel model = new ConfiguredChatModel(config.load());
        // registered exactly as ProjectRuntime registers it, before the previewed agent resolves its
        // tools — a delegation tool a turn could send must be in the answer (UC-WP-88 GIVEN 4)
        AiAgent plan = new AiPlanAgent(model, tools);
        AiAgent review = new AiReviewAgent(model, tools);
        AiAgent dev = new AiDevAgent(model, tools);
        toolSet.addDelegateTool(plan, review, dev, () -> model.getConfig().getAutoCompactAfter());
        AbstractAgent agent = switch (slotId) {
            case AgentModelConfig.PO -> new AiPoAgent(model, tools);
            case AgentModelConfig.PLAN -> new AiPlanAgent(model, tools);
            case AgentModelConfig.REVIEW -> new AiReviewAgent(model, tools);
            case AgentModelConfig.DEV -> new AiDevAgent(model, tools);
            default -> throw new IllegalArgumentException("slot has no tool preview");
        };
        return agent.resolveActiveTools(rawAllowlist);
    }
}
