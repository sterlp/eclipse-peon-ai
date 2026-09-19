package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.shared.GrepHit;
import org.sterl.llmpeon.shared.SearchQuery;

class AiReponseBuilderTest {

    @Test
    void grepCompleteFormatsMatchesModeAndCap() {
        // R8: line hits, unpadded; 1 distinct file >= maxFiles(1) → file cap
        var hits = List.of(new GrepHit("First.java", 1, "match a"),
                new GrepHit("First.java", 3, "match b"));

        String result = AiReponseBuilder.grepComplete(
                hits, SearchQuery.of("match"), 1, AiReponseBuilder.MAX_GREP_LINES, ".java");

        assertThat(result)
                .contains("First.java:1: match a")
                .contains("First.java:3: match b")
                .contains("regex search")
                .contains("result capped at 1 files");
    }

    @Test
    void grepCompleteReportsNoMatchesAndMode() {
        String result = AiReponseBuilder.grepComplete(
                List.of(), SearchQuery.of("foo(bar"), AiReponseBuilder.MAX_GREP_FILES,
                AiReponseBuilder.MAX_GREP_LINES, ".java");

        assertThat(result)
                .contains("no matches")
                .contains("literal search — query is not a valid regex");
    }

    @Test
    void grepLineCapDisclosesShowingOfM() {
        // R8: 500 hits over 20 files → exactly the first 100 lines + disclosure
        var hits = new ArrayList<GrepHit>();
        for (int f = 0; f < 20; f++) {
            for (int l = 1; l <= 25; l++) {
                hits.add(new GrepHit("File" + f + ".java", l, "match " + f + "-" + l));
            }
        }

        String result = AiReponseBuilder.grepComplete(hits, SearchQuery.of("match"),
                AiReponseBuilder.MAX_GREP_FILES, AiReponseBuilder.MAX_GREP_LINES, ".java");

        assertThat(result)
                .contains("File0.java:1: match 0-1")
                .contains("File3.java:25: match 3-25")
                .contains("showing 100 of 500 matched lines — narrow your search")
                .doesNotContain("File4.java");
        long hitLines = result.lines()
                .filter(l -> l.matches("File\\d+\\.java:\\d+: match \\d+-\\d+")).count();
        assertEquals(100, hitLines);
    }

    @Test
    void grepUnderCapShowsAllNoDisclosure() {
        // R8: 50 hits < 100 cap → all shown, no disclosure
        var hits = new ArrayList<GrepHit>();
        for (int l = 1; l <= 50; l++) {
            hits.add(new GrepHit("Only.java", l, "hit " + l));
        }

        String result = AiReponseBuilder.grepComplete(hits, SearchQuery.of("hit"),
                AiReponseBuilder.MAX_GREP_FILES, AiReponseBuilder.MAX_GREP_LINES, ".java");

        assertThat(result)
                .contains("Only.java:1: hit 1")
                .contains("Only.java:50: hit 50")
                .doesNotContain("matched lines — narrow your search");
    }
}
