package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ToolPolicyTest {

    @Test
    void exactMatchEnablesOnlyThatTool() {
        var allow = List.of("get_datetime");
        assertThat(ToolPolicy.enables(allow, "get_datetime")).isTrue();
        assertThat(ToolPolicy.enables(allow, "get_datetime_utc")).isTrue(); // prefix also matches
        assertThat(ToolPolicy.enables(allow, "datetime")).isFalse();
    }

    @Test
    void prefixMatchEnablesFamily() {
        var allow = List.of("document_");
        assertThat(ToolPolicy.enables(allow, "document_read")).isTrue();
        assertThat(ToolPolicy.enables(allow, "document_write")).isTrue();
        assertThat(ToolPolicy.enables(allow, "web_fetch")).isFalse();
    }

    @Test
    void wildcardEnablesEverything() {
        var allow = List.of("*");
        assertThat(ToolPolicy.enables(allow, "anything")).isTrue();
        assertThat(ToolPolicy.enables(allow, "mcp__server__tool")).isTrue();
    }

    @Test
    void mcpPrefixFiltersReadVsWrite() {
        var read = List.of("mcp__docs__search", "mcp__docs__list", "mcp__docs__fetch");
        assertThat(ToolPolicy.enables(read, "mcp__docs__search_docs")).isTrue();
        assertThat(ToolPolicy.enables(read, "mcp__docs__list_libraries")).isTrue();
        assertThat(ToolPolicy.enables(read, "mcp__docs__scrape_docs")).isFalse();
        assertThat(ToolPolicy.enables(read, "mcp__docs__remove_docs")).isFalse();
    }

    @Test
    void emptyOrNullAllowlistEnablesNothing() {
        assertThat(ToolPolicy.enables(List.of(), "any")).isFalse();
        assertThat(ToolPolicy.enables(null, "any")).isFalse();
    }

    @Test
    void blankEntriesAreIgnored() {
        assertThat(ToolPolicy.enables(List.of("  ", ""), "any")).isFalse();
        assertThat(ToolPolicy.enables(List.of("  ", "web"), "web_fetch")).isTrue();
    }
}
