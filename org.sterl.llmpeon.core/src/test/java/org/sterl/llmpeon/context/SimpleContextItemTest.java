package org.sterl.llmpeon.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SimpleContextItemTest {

    @Test
    void render_returnsPlainTextAsIs() {
        SimpleContextItem item = new SimpleContextItem("standing order #1");

        assertThat(item.render()).isEqualTo("standing order #1");
    }

    @Test
    void render_returnsMultilineText() {
        String text = "line 1\nline 2\nline 3";
        SimpleContextItem item = new SimpleContextItem(text);

        assertThat(item.render()).isEqualTo(text);
    }
}
