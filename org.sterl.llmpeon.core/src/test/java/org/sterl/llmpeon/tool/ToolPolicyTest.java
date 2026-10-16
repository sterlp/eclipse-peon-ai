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

    @Test
    void wildcardMinusDebugDeniesDebugKeepsOthers() {
        var allow = List.of("*", "!debugJava*");
        assertThat(ToolPolicy.enables(allow, "debugJavaSetBreakpoint")).isFalse();
        assertThat(ToolPolicy.enables(allow, "eclipseGrepFiles")).isTrue();
        assertThat(ToolPolicy.enables(allow, "mcp__docs__search_docs")).isTrue();
    }

    // Mutation canary (F10): must go red if deny no longer wins over a matching allow token.
    @Test
    void denyWinsOverMatchingAllowToken() {
        var allow = List.of("debugJava", "!debugJavaSetBreakpoint");
        assertThat(ToolPolicy.enables(allow, "debugJavaSetBreakpoint")).isFalse();
        assertThat(ToolPolicy.enables(allow, "debugJavaStepOver")).isTrue();
    }

    @Test
    void prefixAllowWithNegation() {
        var allow = List.of("eclipse", "debugJava", "!debugJava*");
        assertThat(ToolPolicy.enables(allow, "eclipseGrepFiles")).isTrue();
        assertThat(ToolPolicy.enables(allow, "debugJavaGetState")).isFalse();
        assertThat(ToolPolicy.enables(allow, "shellRunCommand")).isFalse();
    }

    @Test
    void negationOnlyMeansAllMinus() {
        var allow = List.of("!debugJava*");
        assertThat(ToolPolicy.enables(allow, "shellRunCommand")).isTrue();
        assertThat(ToolPolicy.enables(allow, "debugJavaGetState")).isFalse();
    }

    // Mutation canary (F10): must go red if a bare `!` starts matching in the deny step.
    @Test
    void bareBangIsNoOp() {
        assertThat(ToolPolicy.enables(List.of("!"), "anything")).isTrue();
        assertThat(ToolPolicy.enables(List.of("*", "!"), "anything")).isTrue();
    }

    // UC-ATC-8
    @Test
    void trailingStarAllowTokenIsPrefixMatch() {
        var allow = List.of("eclipse*");
        assertThat(ToolPolicy.enables(allow, "eclipseGrepFiles")).isTrue();
        assertThat(ToolPolicy.enables(allow, "eclipse")).isTrue();
        assertThat(ToolPolicy.enables(allow, "diskReadFile")).isFalse();
    }

    // UC-ATC-8
    @Test
    void suffixWildcardEquivalentToBarePrefix() {
        for (var name : List.of("eclipseGrepFiles", "diskReadFile", "debugJavaGetState")) {
            assertThat(ToolPolicy.enables(List.of("eclipse*"), name))
                    .as("eclipse* must behave exactly like eclipse for %s", name)
                    .isEqualTo(ToolPolicy.enables(List.of("eclipse"), name));
        }
    }

    @Test
    void suffixWildcardOnUnknownPrefix() {
        var allow = List.of("foo*");
        assertThat(ToolPolicy.enables(allow, "fooBar")).isTrue();
        assertThat(ToolPolicy.enables(allow, "bar")).isFalse();
    }

    @Test
    void doubleStarMatchesNothing() {
        assertThat(ToolPolicy.enables(List.of("**"), "anything")).isFalse();
    }

    @Test
    void parseAllowlistAbsentOrBlankYieldsNull() {
        assertThat(ToolPolicy.parseAllowlist(null)).isNull();
        assertThat(ToolPolicy.parseAllowlist("")).isNull();
        assertThat(ToolPolicy.parseAllowlist("   ")).isNull();
        assertThat(ToolPolicy.parseAllowlist(", ,")).isNull();
    }

    @Test
    void parseAllowlistTrimsAndDropsBlanks() {
        assertThat(ToolPolicy.parseAllowlist("eclipse, !debugJava*")).containsExactly("eclipse", "!debugJava*");
        assertThat(ToolPolicy.parseAllowlist(" eclipse , , disk ")).containsExactly("eclipse", "disk");
    }

    @Test
    void nameFilterAbsentOrEmptyAllowsAll() {
        assertThat(ToolPolicy.nameFilter(null).test("anything")).isTrue();
        assertThat(ToolPolicy.nameFilter("").test("anything")).isTrue();
        assertThat(ToolPolicy.nameFilter("  ").test("anything")).isTrue();
        assertThat(ToolPolicy.nameFilter("eclipse").test("eclipseGrepFiles")).isTrue();
        assertThat(ToolPolicy.nameFilter("eclipse").test("diskReadFile")).isFalse();
    }

    @Test
    void hasEmptyNegationDetectsBareBang() {
        assertThat(ToolPolicy.hasEmptyNegation(List.of("!"))).isTrue();
        assertThat(ToolPolicy.hasEmptyNegation(List.of("!", "x"))).isTrue();
        assertThat(ToolPolicy.hasEmptyNegation(List.of("!x"))).isFalse();
        assertThat(ToolPolicy.hasEmptyNegation(null)).isFalse();
        assertThat(ToolPolicy.hasEmptyNegation(List.of())).isFalse();
    }
}
