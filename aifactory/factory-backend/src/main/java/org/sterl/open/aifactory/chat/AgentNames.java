package org.sterl.open.aifactory.chat;

import java.util.HashMap;
import java.util.Map;

/**
 * Who is speaking, in the factory's own words. The core stamps its <b>agent id</b> on every live line
 * ({@code Peon-PO}, {@code Peon-Dev}, …) because a display name is not core knowledge; this table holds
 * the pairs the factory itself built, so the product can name them and can tell whose conversation the
 * live stream is showing.
 *
 * <p>An origin that is not in here was never built by this factory — it gets no name and is not Jon
 * (R-WP-4: no invented author). Immutable, so a runtime can hand it to its monitor and forget it.</p>
 */
final class AgentNames {

    private final String jonCoreId;
    private final Map<String, String> displayNames;

    private AgentNames(String jonCoreId, Map<String, String> displayNames) {
        this.jonCoreId = jonCoreId;
        this.displayNames = Map.copyOf(displayNames);
    }

    /**
     * @param coreId       the id the core stamps for the agent the conversation belongs to
     * @param displayName  what the browser is told to call him
     */
    static AgentNames ofJon(String coreId, String displayName) {
        return new AgentNames(coreId, Map.of(coreId, displayName));
    }

    /** @return the table plus this agent — the receiver is never mutated, building it stays one chain */
    AgentNames add(String coreId, String displayName) {
        Map<String, String> withMember = new HashMap<>(displayNames);
        withMember.put(coreId, displayName);
        return new AgentNames(jonCoreId, withMember);
    }

    /** The name of a stamped origin, or {@code null} when this factory never built that agent. */
    String display(String origin) {
        // an immutable Map NPEs on a null key — an unclaimed line is one of the unnamed ones
        return origin == null ? null : displayNames.get(origin);
    }

    /** The agent the one conversation of a project belongs to — the only author the live stream shows. */
    boolean isJon(String origin) {
        return jonCoreId.equals(origin);
    }
}
