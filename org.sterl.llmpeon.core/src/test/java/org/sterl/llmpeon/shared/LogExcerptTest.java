package org.sterl.llmpeon.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogExcerptTest {

    @Test
    void filtersLinesByQuery() {
        var excerpt = LogExcerpt.of("INFO one\nERROR two\nINFO three\nERROR four", 10, SearchQuery.of("ERROR"));

        assertThat(excerpt.text()).isEqualTo("ERROR two\nERROR four");
        assertThat(excerpt.matching()).isEqualTo(2);
        assertThat(excerpt.total()).isEqualTo(4);
        assertThat(excerpt.filtered()).isTrue();
    }

    @Test
    void limitAppliesAfterFilter() {
        var excerpt = LogExcerpt.of("ERROR 1\nINFO\nERROR 2\nERROR 3", 2, SearchQuery.of("ERROR"));

        assertThat(excerpt.text()).isEqualTo("ERROR 2\nERROR 3");
        assertThat(excerpt.shown()).isEqualTo(2);
        assertThat(excerpt.matching()).isEqualTo(3);
    }

    @Test
    void withoutQueryTailsContent() {
        var excerpt = LogExcerpt.of("one\ntwo\nthree", 2, null);

        assertThat(excerpt.text()).isEqualTo(FileLines.tail("one\ntwo\nthree", 2));
        assertThat(excerpt.shown()).isEqualTo(2);
        assertThat(excerpt.matching()).isEqualTo(3);
        assertThat(excerpt.total()).isEqualTo(3);
        assertThat(excerpt.filtered()).isFalse();
    }

    @Test
    void headerNamesShownAndTotal() {
        var filtered = LogExcerpt.of("ERROR one\nINFO\nERROR two", 1, SearchQuery.of("ERROR"));
        var unfiltered = LogExcerpt.of("one\ntwo\nthree", 2, null);

        assertThat(filtered.header("mvn-build"))
                .isEqualTo("showing 1 of 2 matching lines (console: mvn-build, total 3) · regex search");
        assertThat(unfiltered.header("mvn-build"))
                .isEqualTo("showing 2 of 3 lines (console: mvn-build)");
    }

    @Test
    void invalidRegexFiltersLiterally() {
        var excerpt = LogExcerpt.of("FOO(BAR\nfoo bar\nfoo(bar", 10, SearchQuery.of("foo(bar"));

        assertThat(excerpt.text()).isEqualTo("FOO(BAR\nfoo(bar");
        assertThat(excerpt.header("build"))
                .isEqualTo("showing 2 of 2 matching lines (console: build, total 3) · literal search — query is not a valid regex");
    }

    @Test
    void emptyContentIsZeroOfZero() {
        var excerpt = LogExcerpt.of("", 10, null);

        assertThat(excerpt.text()).isEmpty();
        assertThat(excerpt.shown()).isZero();
        assertThat(excerpt.matching()).isZero();
        assertThat(excerpt.total()).isZero();
        assertThat(excerpt.header("empty-console"))
                .isEqualTo("showing 0 of 0 lines (console: empty-console)");
    }

    @Test
    void negativeLimitIsClamped() {
        var excerpt = LogExcerpt.of("ERROR one\nERROR two", -1, SearchQuery.of("ERROR"));

        assertThat(excerpt.text()).isEqualTo("ERROR two");
        assertThat(excerpt.shown()).isEqualTo(1);
    }
}
