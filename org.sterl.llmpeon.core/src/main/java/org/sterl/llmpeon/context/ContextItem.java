package org.sterl.llmpeon.context;

/**
 * Renders context content into a string for injection into the agent's conversation memory.
 */
@FunctionalInterface
public interface ContextItem {
    String render();

    /**
     * Short human-readable label for loading reports ({@code "Loading 📋 <label>"}).
     * Default is empty — items without a label load silently.
     */
    default String label() {
        return "";
    }
}
