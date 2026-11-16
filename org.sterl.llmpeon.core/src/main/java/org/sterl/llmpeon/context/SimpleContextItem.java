package org.sterl.llmpeon.context;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.sterl.llmpeon.agent.model.ContextInput;

/**
 * Plain-text context item — renders the provided text as a block with the head
 * {@code context: <label>} (bare {@code context} without a label).
 */
@RequiredArgsConstructor
@EqualsAndHashCode(of = "text")
@ToString
public class SimpleContextItem implements ContextItem {

    private final String label;
    private final String text;

    public SimpleContextItem(String value) {
        this(null, value);
    }

    @Override
    public ContextInput render() {
        return new ContextInput(label == null ? "context" : "context: " + label, text);
    }

    @Override
    public String label() {
        return label;
    }
}
