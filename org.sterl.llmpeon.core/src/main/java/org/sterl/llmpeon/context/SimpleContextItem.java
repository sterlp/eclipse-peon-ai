package org.sterl.llmpeon.context;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Plain-text context item — renders the provided text as-is.
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
    public String render() {
        return text;
    }

    @Override
    public String dedupKey() {
        return null;
    }

    @Override
    public String label() {
        return label;
    }
}
