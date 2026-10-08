package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.shared.GrepHit;
import org.sterl.llmpeon.shared.SearchQuery;
import org.sterl.llmpeon.shared.TextFileTypes;

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
    void grepCompleteEmptyResultLeaksNoFileContent() {
        // GIVEN a large (~70KB) seeded .md file content that does NOT contain the query
        var seed = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            seed.append("line ").append(i).append(" lorem ipsum dolor sit amet\n");
        }
        String content = seed.toString();
        assertThat(content).hasSizeGreaterThanOrEqualTo(50000).doesNotContain("einem text");

        var query = SearchQuery.of("einem text");

        // WHEN the shared match + assemble path runs over that content (type filter .md)
        var hits = query.matchingLines(content).stream()
                .map(lh -> new GrepHit("README.md", lh.line(), lh.text()))
                .toList();
        String result = AiReponseBuilder.grepComplete(hits, query,
                AiReponseBuilder.MAX_GREP_FILES, AiReponseBuilder.MAX_GREP_LINES, ".md");

        // THEN 0 hits -> honest "no matches" + regex mode, and NO seed content leaks into the output
        assertThat(hits).isEmpty();
        assertThat(result)
                .contains("no matches")
                .contains("regex search")
                .doesNotContain("lorem ipsum")
                .doesNotContain("README.md")
                .hasSizeLessThan(2048);
    }

    @Test
    void grepCompleteManyHitsAreCappedAndDisclosed() {
        // GIVEN 500 hits across 125 distinct .md files (125 x 4 lines) — past both caps
        var hits = new ArrayList<GrepHit>();
        for (int f = 0; f < 125; f++) {
            for (int l = 1; l <= 4; l++) {
                hits.add(new GrepHit("md/file_" + f + ".md", l, "einem text " + l));
            }
        }
        assertThat(hits).hasSize(500);
        assertThat(GrepHit.fileCount(hits)).isEqualTo(125);

        // WHEN the shared response-assembly layer renders them with the .md type filter
        String result = AiReponseBuilder.grepComplete(hits, SearchQuery.of("einem text"),
                AiReponseBuilder.MAX_GREP_FILES, AiReponseBuilder.MAX_GREP_LINES, ".md");

        // THEN the line cap renders exactly 100 hit lines — the first 100 in order, not a sample
        var hitLines = result.lines()
                .filter(l -> l.matches(".*\\.md:\\d+: .*"))
                .toList();
        assertThat(hitLines).hasSize(100);
        assertThat(hitLines.get(0)).isEqualTo("md/file_0.md:1: einem text 1");
        assertThat(hitLines.get(99)).isEqualTo("md/file_24.md:4: einem text 4");
        // ... hits beyond the cap are NOT rendered ...
        assertThat(result).doesNotContain("file_25.md");
        // ... the FULL payload stays small (a leaked cap would render ~5x more lines) ...
        assertThat(result).hasSizeLessThan(10000);
        // ... and every truncation is honestly disclosed: showing N of M + file cap + mode
        assertThat(result)
                .contains("showing 100 of 500 matched lines — narrow your search")
                .contains("... result capped at 100 files. Narrow your search path.")
                .contains("regex search");
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
    void searchCompleteDisclosesReachedCap() {
        // limit == size → already cropped → disclosure (trigger >=)
        var results = List.of("a.java", "b.java");

        String result = AiReponseBuilder.searchComplete(results, 2, null);

        assertThat(result)
                .contains("a.java")
                .contains("b.java")
                .contains("capped at 2 — narrow your search");

        // existing suffix stays, disclosure appended after it
        String withSuffix = AiReponseBuilder.searchComplete(results, 2, "Use diskListDirectory");
        assertThat(withSuffix)
                .contains("Use diskListDirectory")
                .contains("capped at 2 — narrow your search");
    }

    @Test
    void searchCompleteUnderCapOrUnlimitedHasNoDisclosure() {
        var results = List.of("a.java", "b.java");

        assertThat(AiReponseBuilder.searchComplete(results, 3, null)).doesNotContain("capped at");
        assertThat(AiReponseBuilder.searchComplete(results, 0, null)).doesNotContain("capped at");
        assertThat(AiReponseBuilder.searchComplete(results, null)).doesNotContain("capped at");
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

    // UC-OD-7
    @Test
    void grepClampsMinifiedLine() {
        // GIVEN one hit whose line is 20000 chars (minified)
        var hits = List.of(new GrepHit("bundle.js", 1, "a".repeat(20_000)));

        // WHEN the shared renderer clamps the line
        String result = AiReponseBuilder.grepComplete(hits, SearchQuery.of("a"),
                AiReponseBuilder.MAX_GREP_FILES, AiReponseBuilder.MAX_GREP_LINES, ".js");

        // THEN the line is prefix + exactly 2000 chars (the 2001st is not there)
        var hitLines = result.lines().filter(l -> l.startsWith("bundle.js:1: ")).toList();
        assertThat(hitLines).hasSize(1);
        assertThat(hitLines.get(0)).isEqualTo("bundle.js:1: " + "a".repeat(2000));
        assertThat(hitLines.get(0)).hasSize("bundle.js:1: ".length() + 2000);
        // ... and the clamp is honestly disclosed
        assertThat(result).contains("1 line(s) truncated at 2000 chars");
    }

    // UC-OD-8
    @Test
    void grepNormalLinesUntouched() {
        // GIVEN hits with normal lines (< 2000 chars)
        var hits = List.of(new GrepHit("A.java", 1, "short a"), new GrepHit("A.java", 2, "short b"));

        // WHEN the renderer runs
        String result = AiReponseBuilder.grepComplete(hits, SearchQuery.of("short"),
                AiReponseBuilder.MAX_GREP_FILES, AiReponseBuilder.MAX_GREP_LINES, ".java");

        // THEN the lines are bit-identical and no truncation is disclosed
        assertThat(result)
                .contains("A.java:1: short a")
                .contains("A.java:2: short b")
                .doesNotContain("truncated");
    }

    // UC-OD-9
    @Test
    void grepNoMatchesUnchanged() {
        // GIVEN zero hits
        String result = AiReponseBuilder.grepComplete(List.of(), SearchQuery.of("nothing"),
                AiReponseBuilder.MAX_GREP_FILES, AiReponseBuilder.MAX_GREP_LINES, null);

        // THEN "no matches" + mode hint + filter hint, exactly as before, no truncation disclosure
        assertThat(result)
                .contains("no matches")
                .contains("regex search")
                .contains(TextFileTypes.filterHint())
                .doesNotContain("truncated");
    }

    private static List<String> lines(String name, int count) {
        var result = new ArrayList<String>();
        for (int i = 1; i <= count; i++) {
            result.add(name + " " + i);
        }
        return result;
    }

    // UC-OD-5
    @Test
    void buildMarkersCappedWithDisclosure() {
        // GIVEN 40 error lines + 80 warning lines (120 markers)
        var errors = lines("error", 40);
        var warnings = lines("warning", 80);

        // WHEN
        String result = AiReponseBuilder.buildMarkers(
                errors, warnings, AiReponseBuilder.MAX_BUILD_MARKERS);

        // THEN exactly 100 marker lines: all errors first, then the first 60 warnings
        var lines = result.lines().toList();
        assertEquals(102, lines.size());
        assertEquals("error 1", lines.get(0));
        assertEquals("error 40", lines.get(39));
        assertEquals("warning 1", lines.get(40));
        assertEquals("warning 60", lines.get(99));
        // and the cap is disclosed as the last line
        assertEquals("", lines.get(100));
        assertEquals("showing 100 of 120 markers", lines.get(101));
    }

    // UC-OD-6
    @Test
    void buildMarkersUnderCapNoDisclosure() {
        // GIVEN 10 error lines + 20 warning lines (30 markers)
        var errors = lines("error", 10);
        var warnings = lines("warning", 20);

        // WHEN
        String result = AiReponseBuilder.buildMarkers(
                errors, warnings, AiReponseBuilder.MAX_BUILD_MARKERS);

        // THEN all 30 lines present, no disclosure
        assertThat(result).contains("error 10").contains("warning 20");
        var lines = result.lines().toList();
        assertEquals(30, lines.size());
        assertThat(result).doesNotContain("showing");
    }

    @Test
    void buildMarkersUnlimitedAndExactCapNoDisclosure() {
        // cap 0 = unlimited → all lines, no disclosure
        String unlimited = AiReponseBuilder.buildMarkers(lines("error", 5), lines("warning", 7), 0);
        var unlimitedLines = unlimited.lines().toList();
        assertEquals(12, unlimitedLines.size());
        assertThat(unlimited).doesNotContain("showing");

        // total == cap → nothing cropped → no disclosure
        String exactCap = AiReponseBuilder.buildMarkers(lines("error", 60), lines("warning", 40), 100);
        assertEquals(100, exactCap.lines().count());
        assertThat(exactCap).doesNotContain("showing");
    }

    @Test
    void buildMarkersMoreErrorsThanCap() {
        // 120 errors + 0 warnings, cap 100 → 100 error lines, warnings never backfilled
        String result = AiReponseBuilder.buildMarkers(lines("error", 120), List.of(), 100);

        var lines = result.lines().toList();
        assertEquals(102, lines.size());
        assertEquals("error 100", lines.get(99));
        assertEquals("showing 100 of 120 markers", lines.get(101));
    }

}
