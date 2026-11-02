package org.sterl.open.aifactory.config.agent;

import java.util.List;

import org.sterl.llmpeon.ai.AgentModelConfig;

/**
 * The slots the config page offers (Q9): the agents the factory actually builds plus the compact slot,
 * which is no agent but is configured the same way. One source for API, UI and test — a list kept twice
 * is a slot that exists on the page and does nothing.
 *
 * <p>The ids are the core's stable slot names, so an override written here is the same record the core's
 * own {@code llm.agent.<id>.*} keys address. Paco and custom agents are absent on purpose: the factory
 * runs neither, so a row for them would promise a setting nothing reads.</p>
 */
public final class AgentSlots {

    /** A slot as the page names it: the core's id, a display name the browser can show. */
    public record AgentSlot(String id, String name) {
    }

    /** The order of R-WP-23: Jon, Plan, Dev, Review, Search, Compact. */
    private static final List<AgentSlot> SLOTS = List.of(
            new AgentSlot(AgentModelConfig.PO, "Jon"),
            new AgentSlot(AgentModelConfig.PLAN, "Plan"),
            new AgentSlot(AgentModelConfig.DEV, "Dev"),
            new AgentSlot(AgentModelConfig.REVIEW, "Review"),
            new AgentSlot(AgentModelConfig.SEARCH, "Search"),
            new AgentSlot(AgentModelConfig.COMPACT, "Compact"));

    private AgentSlots() {
    }

    public static List<AgentSlot> all() {
        return SLOTS;
    }

    public static boolean knows(String id) {
        return SLOTS.stream().anyMatch(slot -> slot.id().equals(id));
    }
}
