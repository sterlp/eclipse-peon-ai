package org.sterl.llmpeon.context;

import org.junit.Test;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.poagent.AiPoAgent;

import junit.framework.TestCase;

public class AgentsMdContextItemTest extends TestCase {
    @Test
    public void test_peon_plam() {
        var items = AgentsMdContextItem.itemsFor(AiPlanAgent.NAME, () -> null);
        assertTrue(items.get(1).toString() + " is missing " + AiPlanAgent.NAME, items.get(1).toString().contains("AGENTS-PLAN.md"));
    }

    @Test
    public void test_peon_dev() {
        var items = AgentsMdContextItem.itemsFor(AiDevAgent.NAME, () -> null);
        assertTrue(items.get(1).toString() + " is missing " + AiDevAgent.NAME, items.get(1).toString().contains("AGENTS-DEV.md"));
    }

    /**
     * BUG-1 pin (ADR-0079 Clean Break): Jon's own standing-orders file is named after his id, so the
     * resolver must offer every case variant of <code>AGENTS-Jon.md</code> — and must never fall back to
     * the pre-rename <code>AGENTS-PO.md</code>, which would be an alias.
     */
    @Test
    public void test_jon_resolvesOnlyToCleanBreakNames() {
        // GIVEN Jon's built-in name
        var items = AgentsMdContextItem.itemsFor(AiPoAgent.NAME, () -> null);
        // WHEN his agent-specific candidates are resolved
        var agentItem = items.get(1).toString();

        // THEN all case variants of his own name are offered …
        assertTrue(agentItem + " is missing AGENTS-Jon.md", agentItem.contains("AGENTS-Jon.md"));
        assertTrue(agentItem + " is missing agents-jon.md", agentItem.contains("agents-jon.md"));
        assertTrue(agentItem + " is missing AGENTS-JON.md", agentItem.contains("AGENTS-JON.md"));
        // AND the ork-era key never returns as a silent alias
        assertFalse(agentItem + " still resolves the pre-rename AGENTS-PO.md", agentItem.contains("AGENTS-PO.md"));
    }
}
