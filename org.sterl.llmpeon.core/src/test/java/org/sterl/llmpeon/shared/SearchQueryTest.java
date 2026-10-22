package org.sterl.llmpeon.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchQueryTest {

    @Test
    void validPatternUsesRegex() {
        var query = SearchQuery.of("Model.*Widget");

        assertThat(query.literal()).isFalse();
        assertThat(query.count("ModelBigWidget\nModelService")).isEqualTo(1);
        assertThat(query.modeHint()).isEqualTo("regex search");
    }

    @Test
    void invalidPatternFallsBackToLiteral() {
        var query = SearchQuery.of("foo(bar");

        assertThat(query.literal()).isTrue();
        assertThat(query.count("FOO(BAR and foo(bar")).isEqualTo(2);
        assertThat(query.modeHint()).isEqualTo("literal search — query is not a valid regex");
    }

    @Test
    void isCachedPerQuery() {
        assertThat(SearchQuery.of("x")).isSameAs(SearchQuery.of("x"));
    }

    @Test
    void matchesUsesTheSelectedMode() {
        assertThat(SearchQuery.of("Model.*Widget").matches("a ModelBigWidget here")).isTrue();
        assertThat(SearchQuery.of("foo(bar").matches("a FOO(BAR here")).isTrue();
        assertThat(SearchQuery.of("foo(bar").matches("foo bar")).isFalse();
    }

    // R-BC-2 (org.sterl.llmpeon.core/docs/bounded-caches.md)
    // 129 fresh distinct keys via of() -> the static cache holds exactly 128 (cap), robust against
    // any pre-existing entries (129 fresh distinct puts fill the cache and evict down to the cap).
    // Mutation: unbounded cache (old ConcurrentHashMap) -> size >= 129 -> red.
    @Test
    void searchQueryCacheBoundedAt128() {
        for (int i = 0; i <= 128; i++) {
            SearchQuery.of("bc-cap-" + i);
        }

        assertThat(SearchQuery.CACHE.size()).isEqualTo(128);
    }
}
