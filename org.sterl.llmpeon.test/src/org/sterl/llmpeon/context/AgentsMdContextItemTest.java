package org.sterl.llmpeon.context;

import org.junit.Test;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;

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
}
