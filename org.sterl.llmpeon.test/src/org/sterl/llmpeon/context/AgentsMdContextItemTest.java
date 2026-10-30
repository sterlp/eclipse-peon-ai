package org.sterl.llmpeon.context;

import org.junit.Test;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.poagent.AiPoAgent;

import junit.framework.TestCase;

public class AgentsMdContextItemTest extends TestCase {
    @Test
    public void test_planOffersAllCaseVariants() {
        var items = AgentsMdContextItem.itemsFor(AiPlanAgent.NAME, () -> null);
        assertTrue(items.get(1).toString() + " is missing " + AiPlanAgent.NAME, items.get(1).toString().contains("AGENTS-PLAN.md"));
    }

    @Test
    public void test_devOffersAllCaseVariants() {
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

    /**
     * B2 pin (ADR-0079 Clean Break): a display name is a name, not a prefix to strip — the former
     * <code>Peon-</code> special path made a custom agent named <code>Peon-Dev</code> silently inherit
     * the built-in Dev agent's standing orders.
     */
    @Test
    public void test_customPeonNameIsNotStripped() {
        // GIVEN a custom agent whose display name starts with the former built-in prefix
        var agentItem = AgentsMdContextItem.itemsFor("Peon-Dev", () -> null).get(1).toString();

        // THEN its own name is offered in the case variants …
        assertTrue(agentItem + " is missing AGENTS-Peon-Dev.md", agentItem.contains("AGENTS-Peon-Dev.md"));
        assertTrue(agentItem + " is missing AGENTS-PEON-DEV.md", agentItem.contains("AGENTS-PEON-DEV.md"));
        // AND the built-in Dev agent's file is NOT reached by stripping the prefix
        assertFalse(agentItem + " strips Peon- down to the built-in AGENTS-DEV.md",
                agentItem.contains("AGENTS-DEV.md"));
    }
}
