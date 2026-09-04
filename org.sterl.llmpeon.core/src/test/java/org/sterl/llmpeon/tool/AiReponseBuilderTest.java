package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.shared.SearchQuery;

class AiReponseBuilderTest {

    @Test
    void grepCompleteFormatsMatchesModeAndCap() {
        var matches = new LinkedHashMap<String, Integer>();
        matches.put("First.java", 2);

        String result = AiReponseBuilder.grepComplete(matches, SearchQuery.of("match"), 1, ".java");

        assertThat(result)
                .contains("First.java: 2 occurrence(s)")
                .contains("regex search")
                .contains("result capped at 1 files");
    }

    @Test
    void grepCompleteReportsNoMatchesAndMode() {
        String result = AiReponseBuilder.grepComplete(
                new LinkedHashMap<>(), SearchQuery.of("foo(bar"), AiReponseBuilder.MAX_GREP_FILES, ".java");

        assertThat(result)
                .contains("no matches")
                .contains("literal search — query is not a valid regex");
    }
}
