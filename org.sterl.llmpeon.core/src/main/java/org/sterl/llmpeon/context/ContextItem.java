package org.sterl.llmpeon.context;

import java.util.LinkedList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.agent.model.TaggedResult;

/**
 * Renders context content into a {@link TaggedResult} block for injection into the
 * agent's conversation memory.
 */
@FunctionalInterface
public interface ContextItem {

    static List<ContextItem> newList(List<ContextItem> items, ContextItem item) {
        var result = new LinkedList<>(items);
        result.add(item);
        return result;
    }

    /**
     * Plain-text context item — renders as a block with the bare head {@code context}.
     */
    static ContextItem of(String text) {
        return new SimpleContextItem(null, text);
    }

    /**
     * Renders the context content.
     * @return the block, or {@code null} if there is nothing to inject (e.g. file/project missing) —
     *         the item is skipped silently, no exception, no loading status.
     */
    @Nullable
    TaggedResult render();

    /**
     * Deduplication marker checked in the history BEFORE this item is rendered.
     * @return the block's exact start line ({@code TaggedResult.PREFIX + head}), or {@code null}
     *         to dedupe by rendered content.
     */
    default String dedupKey() {
        return null;
    }

    /**
     * Short human-readable label for loading reports ({@code "📋 Loading <label>"}).
     * Default is empty — items without a label load silently.
     */
    default String label() {
        return null;
    }
}
