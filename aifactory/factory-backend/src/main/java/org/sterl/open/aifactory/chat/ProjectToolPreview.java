package org.sterl.open.aifactory.chat;

import java.util.List;

import org.springframework.stereotype.Service;

import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.agent.AbstractAgent;
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
 * agent a write root nobody gave it. The runtime-added {@code PoDelegateTool} is not part of that
 * set, so it is not in the answer — the same set a project's Jon starts with.</p>
 */
@Service
public class ProjectToolPreview implements AgentToolPreview {

    private final FactoryConfigService config;

    public ProjectToolPreview(FactoryConfigService config) {
        this.config = config;
    }

    @Override
    public List<String> activeTools(String slotId, String rawAllowlist) {
        ToolService tools = JonToolSet.forProject(TmpRoots.systemTmp()).tools();
        if (AgentModelConfig.SEARCH.equals(slotId)) {
            // the slot's restriction is the tool's own filter — not a second one invented here
            return tools.getTool(SearchAgentTool.class)
                    .map(search -> search.resolveActiveTools(rawAllowlist))
                    .orElseThrow(() -> new IllegalArgumentException("search tool is not registered"));
        }
        ConfiguredChatModel model = new ConfiguredChatModel(config.load());
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
