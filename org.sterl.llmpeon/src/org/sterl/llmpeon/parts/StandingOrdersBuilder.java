package org.sterl.llmpeon.parts;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.sterl.llmpeon.PeonMode;
import org.sterl.llmpeon.parts.agent.AgentModeService;
import org.sterl.llmpeon.parts.agentsmd.AgentsMdService;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.template.TemplateContext;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;

/**
 * Assembles the standing-orders system messages that are prepended each call.
 * Includes the selected resource context, any AGENTS.md content, and a plan
 * file hint when in AGENT mode.
 */
public class StandingOrdersBuilder {

    private StandingOrdersBuilder() {}

    public static List<ChatMessage> build(
            IResource selectedResource,
            AgentsMdService agentsMdService,
            TemplateContext context,
            PeonMode currentMode,
            AgentModeService agentMode) {

        var orders = new ArrayList<ChatMessage>();
        if (selectedResource != null) {
            orders.add(SystemMessage.from("Selected resource: " + JdtUtil.pathOf(selectedResource)));
        }
        if (agentsMdService.hasAgentFile()) {
            agentsMdService.agentMessage(context).ifPresent(orders::add);
        }
        if (currentMode == PeonMode.AGENT && agentMode.hasPlan()) {
            orders.add(SystemMessage.from(agentMode.planPathHint()));
        }
        return orders;
    }
}
