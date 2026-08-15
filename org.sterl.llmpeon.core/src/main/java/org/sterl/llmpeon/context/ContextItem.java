package org.sterl.llmpeon.context;

/**
 * Renders context content into a string for injection into the agent's conversation memory.
 */
@FunctionalInterface
public interface ContextItem {
    String render();
}
