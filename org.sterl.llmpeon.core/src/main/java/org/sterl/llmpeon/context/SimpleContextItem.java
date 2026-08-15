package org.sterl.llmpeon.context;

import lombok.RequiredArgsConstructor;

/**
 * Plain-text context item — renders the provided text as-is.
 */
@RequiredArgsConstructor
public class SimpleContextItem implements ContextItem {

    private final String text;

    @Override
    public String render() {
        return text;
    }
}
