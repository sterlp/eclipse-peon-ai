package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.model.SimpleMessage;
import org.sterl.llmpeon.tool.tools.WebSearchException;
import org.sterl.llmpeon.tool.tools.WebSearchTool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;

/**
 * {@code webSearch} rendering and honesty (org.sterl.llmpeon.core/docs/web-tools.md R-WEB-10/R-WEB-12):
 * numbered title/URL/snippet blocks, the caps with their disclosures, the honest zero and the error
 * wording that never degrades into "0 results" — plus the rate-limit way forward that never retries
 * (R-WEB-14). Stub engine only — never the network.
 */
@Timeout(30)
class WebSearchToolTest {

    private static final String NL = System.lineSeparator();
    private static final String QUERY = "eclipse peon ai";

    WebSearchTool tool;

    // UC-WEB-10
    @Test
    void webSearchReturnsDefaultMaxEightWithDisclosure() {
        // GIVEN 20 hits from an engine that ignores the requested limit
        tool = new WebSearchTool(new StubEngine(hits(20), 20));

        // WHEN webSearch(query) without maxResults
        String result = tool.webSearch(QUERY, null);

        // THEN 8 numbered blocks, the 9th hit never reaches the context, and the footer names M honestly
        assertThat(result).startsWith("1. Title 1").contains("8. Title 8").doesNotContain("9. Title 9");
        assertThat(countBlocks(result)).isEqualTo(8);
        assertThat(result).endsWith("showing 8 of 20 results");
    }

    // UC-WEB-10
    @Test
    void webSearchReturnsAllThreeWithoutDisclosure() {
        tool = new WebSearchTool(new StubEngine(hits(3), 3));

        String result = tool.webSearch(QUERY, null);

        // GIVEN 3 hits WHEN searched THEN exactly 3, nothing invented, nothing hidden — so no footer
        assertThat(countBlocks(result)).isEqualTo(3);
        assertThat(result).doesNotContain("showing").doesNotContain("capped");
        assertThat(result).endsWith("   Snippet 3");
    }

    // UC-WEB-10
    @Test
    void webSearchHonorsRequestedMaxResults() {
        tool = new WebSearchTool(new StubEngine(hits(15), 15));

        String result = tool.webSearch(QUERY, 3);

        assertThat(countBlocks(result)).isEqualTo(3);
        assertThat(result).doesNotContain("4. Title 4").endsWith("showing 3 of 15 results");
    }

    // UC-WEB-10
    @Test
    void webSearchCapsRequestedMaxResultsAtTen() {
        tool = new WebSearchTool(new StubEngine(hits(20), 20));

        // GIVEN maxResults 50 WHEN searched THEN the hard max, and the request itself disclosed
        String result = tool.webSearch(QUERY, 50);

        assertThat(countBlocks(result)).isEqualTo(10);
        assertThat(result).endsWith("maxResults capped at 10 (requested 50)" + NL + "showing 10 of 20 results");
    }

    // UC-WEB-10
    @Test
    void webSearchTreatsNullOrZeroMaxResultsAsDefault() {
        StubEngine stub = new StubEngine(hits(20), 20);
        tool = new WebSearchTool(stub);

        for (Integer requested : new Integer[] {null, 0, -3}) {
            // GIVEN no usable maxResults WHEN searched THEN the default 8, also as hint to the engine
            String result = tool.webSearch(QUERY, requested);

            assertThat(countBlocks(result)).as("requested %s", requested).isEqualTo(8);
            assertThat(result).as("requested %s", requested).endsWith("showing 8 of 20 results");
            assertThat(stub.lastRequest.maxResults()).as("requested %s", requested).isEqualTo(8);
        }
    }

    // UC-WEB-10
    @Test
    void webSearchPassesQueryAndEffectiveLimitToTheEngine() {
        StubEngine stub = new StubEngine(hits(1), 1);
        tool = new WebSearchTool(stub);

        // GIVEN a request within the cap WHEN searching THEN the engine gets the query and the limit as asked
        tool.webSearch(QUERY, 3);
        assertThat(stub.lastRequest.searchTerms()).isEqualTo(QUERY);
        assertThat(stub.lastRequest.maxResults()).isEqualTo(3);

        // AND GIVEN a request beyond the hard max WHEN searching THEN it reaches the engine already clamped
        tool.webSearch(QUERY, 50);
        assertThat(stub.lastRequest.maxResults()).isEqualTo(10);

        // AND GIVEN no request WHEN searching THEN the default is sent, not null
        tool.webSearch(QUERY, null);
        assertThat(stub.lastRequest.maxResults()).isEqualTo(8);
    }

    // UC-WEB-10
    @Test
    void webSearchRendersNumberedTitleUrlSnippetBlocks() {
        List<WebSearchOrganicResult> hits = List.of( //
                new WebSearchOrganicResult("  Eclipse   Peon  ", URI.create("https://example.org/a"), "line one\n\tline  two", null),
                new WebSearchOrganicResult("Second", URI.create("https://example.org/b"), "x".repeat(250), null),
                new WebSearchOrganicResult("NoSnippet", URI.create("https://example.org/c"), null, null));
        tool = new WebSearchTool(new StubEngine(hits, 3));

        String result = tool.webSearch(QUERY, null);

        // GIVEN web content hits WHEN rendered THEN one line per field, whitespace collapsed, snippet
        // capped at 200 chars total (199 + ellipsis), a hit without snippet simply has no third line,
        // and the cut itself is named as the last line
        assertThat(result).isEqualTo(String.join(NL, //
                "1. Eclipse Peon", //
                "   https://example.org/a", //
                "   line one line two", //
                "2. Second", //
                "   https://example.org/b", //
                "   " + "x".repeat(199) + "…", //
                "3. NoSnippet", //
                "   https://example.org/c", //
                "snippets truncated to 200 characters"));
        assertThat(result).doesNotContain("x".repeat(200));
    }

    // R-WEB-10
    @Test
    void webSearchDisclosesSnippetTruncation() {
        List<WebSearchOrganicResult> hits = List.of( //
                new WebSearchOrganicResult("Over the limit", URI.create("https://example.org/a"), "y".repeat(201), null),
                new WebSearchOrganicResult("Within the limit", URI.create("https://example.org/b"), "short one", null));
        tool = new WebSearchTool(new StubEngine(hits, 4));

        String result = tool.webSearch(QUERY, null);

        // GIVEN one snippet beyond 200 characters WHEN rendered THEN the ellipsis is named as a limit
        // of its own (AGENTS.md: every truncation appears in its own output), after the count footer
        assertThat(result).endsWith("showing 2 of 4 results" + NL + "snippets truncated to 200 characters");
    }

    // R-WEB-10
    @Test
    void webSearchOmitsTruncationLineWhenAllSnippetsShort() {
        List<WebSearchOrganicResult> hits = List.of( //
                new WebSearchOrganicResult("Exactly at the limit", URI.create("https://example.org/a"), "z".repeat(200), null),
                new WebSearchOrganicResult("Short", URI.create("https://example.org/b"), "short one", null),
                new WebSearchOrganicResult("No snippet", URI.create("https://example.org/c"), null, null));
        tool = new WebSearchTool(new StubEngine(hits, 3));

        String result = tool.webSearch(QUERY, null);

        // GIVEN nothing was cut — 200 characters is still whole — WHEN rendered THEN no line claims a
        // limit that never happened, and no ellipsis appears anywhere
        assertThat(result).doesNotContain("snippets truncated").doesNotContain("…");
        assertThat(result).endsWith("   https://example.org/c");
    }

    // UC-WEB-10
    @Test
    void webSearchSkipsHitsWithoutAbsoluteHttpUrl() {
        List<WebSearchOrganicResult> hits = List.of( //
                new WebSearchOrganicResult("Relative", URI.create("/relative"), null, null),
                new WebSearchOrganicResult("Ftp", URI.create("ftp://example.org/x"), null, null),
                new WebSearchOrganicResult("Usable", URI.create("https://example.org/x"), null, null));
        tool = new WebSearchTool(new StubEngine(hits, 3));

        String result = tool.webSearch(QUERY, null);

        // THEN only the hit that webFetchAsMarkdown can read, renumbered — and the footer still counts all 3
        assertThat(result).isEqualTo(String.join(NL, //
                "1. Usable", //
                "   https://example.org/x", //
                "showing 1 of 3 results"));
    }

    // UC-WEB-10
    @Test
    void webSearchFooterDisclosesHonestTotalInBothDirections() {
        // GIVEN an engine that truncated to 8 itself but knows 100 hits exist WHEN rendered THEN M is 100
        tool = new WebSearchTool(new StubEngine(hits(8), 100));
        assertThat(tool.webSearch(QUERY, null)).endsWith("showing 8 of 100 results");

        // AND GIVEN an engine that reports no total WHEN rendered THEN M never shrinks below its own hits
        tool = new WebSearchTool(new StubEngine(hits(20), 0));
        assertThat(tool.webSearch(QUERY, null)).endsWith("showing 8 of 20 results");
    }

    // UC-WEB-12
    @Test
    void webSearchReportsZeroResultsHonestly() {
        tool = new WebSearchTool(new StubEngine(List.of(), 0));

        String result = tool.webSearch(QUERY, null);

        // GIVEN a search with no hits THEN a named zero with a way on, not an empty body and not an error
        assertThat(result).isEqualTo(
                "0 results for '" + QUERY + "' (DuckDuckGo returned no hits; try a shorter or different query).");
        assertThat(result).doesNotContain("failed", "showing");
    }

    // UC-WEB-12
    @Test
    void webSearchDoesNotCallUnusableHitsAnEmptySearch() {
        List<WebSearchOrganicResult> hits = List.of(new WebSearchOrganicResult("Relative", URI.create("/relative"), null, null));
        tool = new WebSearchTool(new StubEngine(hits, 1));

        String result = tool.webSearch(QUERY, null);

        // THEN the zero is honest about its cause — the engine did answer, it just gave nothing readable
        assertThat(result).contains("no hit of 1 had an absolute http(s) URL");
        assertThat(result).doesNotContain("DuckDuckGo returned no hits");
    }

    // UC-WEB-12
    @Test
    void webSearchReportsRateLimitAsError() {
        failingEngine("rejected (HTTP 429 — rate limit or bot check)");

        String result = tool.webSearch(QUERY, null);

        assertThat(result).startsWith("webSearch failed for '" + QUERY + "': rejected (HTTP 429") //
                .contains("rate limit") //
                .contains(". No results were returned — this is an error, not an empty result set.");
        assertThat(result).doesNotContain("0 results");
    }

    // R-WEB-14
    // UC-WEB-14
    @Test
    void webSearchRateLimitErrorExplainsTheWayForward() {
        // GIVEN DuckDuckGo rejects the search with a bot-check status
        var stub = failingEngine("rejected (HTTP 202 — rate limit or bot check)");

        // WHEN
        String result = tool.webSearch(QUERY, null);

        // THEN — cause, the R-WEB-12 honesty tail and the way forward, still never a zero
        assertThat(result) //
                .startsWith("webSearch failed for '" + QUERY + "': rejected (HTTP 202") //
                .contains(". No results were returned — this is an error, not an empty result set.") //
                .contains("Do not retry immediately") //
                .contains("webFetchAsMarkdown") //
                .doesNotContain("0 results");

        // AND exactly one call reached the endpoint — the guidance must not be paid for with a retry
        assertThat(stub.callCount).isEqualTo(1);
    }

    // R-WEB-14
    // UC-WEB-14
    @Test
    void webSearchStatusOnlyCauseStillGetsGuidance() {
        // GIVEN a rejection whose message names only the status — neither "rate limit" nor "bot check"
        var stub = failingEngine("rejected (HTTP 429)");

        // WHEN
        String result = tool.webSearch(QUERY, null);

        // THEN — the status alone is enough: the guidance is there, so an engine that rewords its
        // message cannot silently drop the way forward
        assertThat(result) //
                .startsWith("webSearch failed for '" + QUERY + "': rejected (HTTP 429)") //
                .contains(". No results were returned — this is an error, not an empty result set.") //
                .contains("Do not retry immediately") //
                .contains("webFetchAsMarkdown") //
                .doesNotContain("0 results");
        assertThat(stub.callCount).isEqualTo(1);
    }


    // R-WEB-14
    // UC-WEB-14
    @Test
    void webSearchToolDescriptionWarnsAboutRateLimit() throws NoSuchMethodException {
        // GIVEN the tool schema an agent reads before its first call
        var annotation = WebSearchTool.class //
                .getMethod("webSearch", String.class, Integer.class) //
                .getAnnotation(Tool.class);
        String description = String.join(" ", annotation.value());

        // THEN — rate limit and pacing are named, and the existing caps survive the extension
        assertThat(description) //
                .contains("rate-limits") //
                .contains("one at a time") //
                .contains("not several in the same turn") //
                .contains("don't retry") //
                .contains("max 8") //
                .contains("webFetchAsMarkdown");
    }


    // UC-WEB-12
    @Test
    void webSearchReportsServerError() {
        failingEngine("request failed (HTTP 500)");

        assertThat(tool.webSearch(QUERY, null)) //
                .contains("webSearch failed for '" + QUERY + "': request failed (HTTP 500)") //
                .doesNotContain("0 results");
    }

    // UC-WEB-12
    @Test
    void webSearchTreatsUnknownLayoutAsError() {
        failingEngine("layout not recognized (0 parseable results)");

        // THEN a layout drift is an error, never the zero the agent could mistake for "nothing exists"
        assertThat(tool.webSearch(QUERY, null)) //
                .contains("layout not recognized") //
                .doesNotContain("0 results");
    }

    // UC-WEB-12
    @Test
    void webSearchReportsNetworkError() {
        failingEngine("network error: Connection refused");

        assertThat(tool.webSearch(QUERY, null)) //
                .contains("network error: Connection refused") //
                .endsWith(". No results were returned — this is an error, not an empty result set.");
    }

    // UC-WEB-10, UC-WEB-12
    @Test
    void webSearchReportsProgressAndProblemToTheMonitor() {
        List<String> tools = new ArrayList<>();
        List<String> problems = new ArrayList<>();

        // GIVEN hits WHEN the search completes THEN one done line naming the exact result size (R-RS-1)
        tool = new WebSearchTool(new StubEngine(hits(2), 2));
        tool.withToolRequest(request(monitorCapturing(tools, problems)));
        String result = tool.webSearch(QUERY, null);
        assertThat(tools).containsExactly("Searched '" + QUERY + "': 2 results " + StringUtil.charsSuffix(result));
        assertThat(problems).isEmpty();

        // AND GIVEN no hits WHEN it completes THEN the done line counts zero, still with its size
        tools.clear();
        tool = new WebSearchTool(new StubEngine(List.of(), 0));
        tool.withToolRequest(request(monitorCapturing(tools, problems)));
        String empty = tool.webSearch(QUERY, null);
        assertThat(tools).containsExactly("Searched '" + QUERY + "': 0 results " + StringUtil.charsSuffix(empty));

        // AND GIVEN a failing engine WHEN it fails THEN the problem line and no done line
        tools.clear();
        tool = new WebSearchTool(new StubEngine(List.of(), 0, new WebSearchException("network error: refused")));
        tool.withToolRequest(request(monitorCapturing(tools, problems)));
        tool.webSearch(QUERY, null);
        assertThat(problems).containsExactly("webSearch failed for '" + QUERY + "' - network error: refused");
        assertThat(tools).isEmpty();
    }

    private StubEngine failingEngine(String cause) {
        var stub = new StubEngine(List.of(), 0, new WebSearchException(cause));
        tool = new WebSearchTool(stub);
        return stub;
    }

    private static List<WebSearchOrganicResult> hits(int count) {
        return IntStream.rangeClosed(1, count).boxed() //
                .map(i -> new WebSearchOrganicResult("Title " + i, URI.create("https://example.org/" + i), "Snippet " + i, null)) //
                .toList();
    }

    private static long countBlocks(String result) {
        return result.lines().filter(line -> line.matches("^\\d+\\. .+")).count();
    }

    /** Full request wiring (memory + chatModel are required fields) — the model is never called by this tool. */
    private static ToolLoopRequest request(AiMonitor monitor) {
        var config = LlmConfig.builder().model("test").build();
        var cm = new StreamMock().buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("unused"))
                .build());
        return ToolLoopRequest.builder()
                .memory(new ThreadSafeMemory())
                .chatModel(new ConfiguredChatModel(config, cm))
                .monitor(monitor)
                .build();
    }

    private static AiMonitor monitorCapturing(List<String> tools, List<String> problems) {
        return new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public void onTool(String message) { tools.add(message); }
            @Override public void onProblem(String message) { problems.add(message); }
        };
    }

    /**
     * Deterministic engine: fixed hits and total, captures the request. It deliberately IGNORES
     * {@code maxResults} — every rendering assertion below is therefore also a proof that the tool
     * caps authoritatively instead of trusting the engine.
     */
    private static class StubEngine implements WebSearchEngine {

        private final List<WebSearchOrganicResult> hits;
        private final long total;
        private final RuntimeException failure;
        WebSearchRequest lastRequest;
        /** R-WEB-14: "no retry reached the endpoint" is only provable if the stub counts its calls. */
        int callCount;

        StubEngine(List<WebSearchOrganicResult> hits, long total) {
            this(hits, total, null);
        }

        StubEngine(List<WebSearchOrganicResult> hits, long total, RuntimeException failure) {
            this.hits = hits;
            this.total = total;
            this.failure = failure;
        }

        @Override
        public WebSearchResults search(WebSearchRequest request) {
            callCount++;
            lastRequest = request;
            if (failure != null) throw failure;
            return WebSearchResults.from(WebSearchInformationResult.from(total), hits);
        }
    }
}
