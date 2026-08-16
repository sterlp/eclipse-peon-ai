package org.sterl.llmpeon.context;

/**
 * Renders context content into a string for injection into the agent's conversation memory.
 */
@FunctionalInterface
public interface ContextItem {

    /**
     * Renders the context content.
     * @return the content, or {@code null} if there is nothing to inject (e.g. file/project missing) —
     *         the item is skipped silently, no exception, no loading status.
     */
    String render();

    /**
     * Deduplication marker checked in the history BEFORE this item is rendered.
     * @return the marker string (files: full workspace path), or {@code null} to dedupe by rendered content.
     */
    default String dedupKey() {
        return null;
    }

    /**
     * Short human-readable label for loading reports ({@code "Loading 📋 <label>"}).
     * Default is empty — items without a label load silently.
     */
    default String label() {
        return null;
    }
}
