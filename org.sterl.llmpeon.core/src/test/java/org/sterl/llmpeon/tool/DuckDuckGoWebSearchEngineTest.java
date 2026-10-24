package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sterl.llmpeon.tool.tools.DuckDuckGoWebSearchEngine;
import org.sterl.llmpeon.tool.tools.WebSearchException;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;

/**
 * DuckDuckGo engine transport (org.sterl.llmpeon.core/docs/web-tools.md R-WEB-10/R-WEB-12,
 * docs/adr/0074-websearch-eigener-ddg-client.md): layout mapping, the honest zero, and the
 * error classes that must never degrade into "0 results". Served by a local HttpServer — never the
 * real endpoint.
 */
@Timeout(30)
class DuckDuckGoWebSearchEngineTest {

    private static final String QUERY = "eclipse peon ai";

    HttpServer server;
    DuckDuckGoWebSearchEngine engine;
    volatile String lastRawQuery;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        engine = engineAt("/html");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private DuckDuckGoWebSearchEngine engineAt(String context) {
        return new DuckDuckGoWebSearchEngine("http://localhost:" + server.getAddress().getPort() + context);
    }

    private void serve(int status, String body) {
        serve("/html", status, body);
    }

    private void serve(String context, int status, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        server.createContext(context, exchange -> {
            lastRawQuery = exchange.getRequestURI().getRawQuery();
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (var out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
        });
    }

    // R-WEB-10
    @Test
    void engineParsesTitleUrlAndSnippet() {
        serve(200, hitsPage(3));

        WebSearchResults results = engine.search(QUERY);

        assertThat(results.results()).hasSize(3);
        assertThat(results.results().getFirst().title()).isEqualTo("Title 1");
        assertThat(results.results().getFirst().url()).isEqualTo(URI.create("https://example.org/1"));
        assertThat(results.results().getFirst().snippet()).isEqualTo("Snippet 1");
        assertThat(results.searchInformation().totalResults()).isEqualTo(3L);
    }

    // R-WEB-10
    @Test
    void engineSendsUrlEncodedQuery() {
        serve(200, hitsPage(1));

        engine.search(QUERY);

        assertThat(lastRawQuery).isEqualTo("q=eclipse+peon+ai");
    }

    // R-WEB-10
    @Test
    void engineUnwrapsDuckDuckGoRedirectUrls() {
        serve(200, page(resultBlock("",
                "//duckduckgo.com/l/?uddg=https%3A%2F%2Fdocs.langchain4j.dev%2Ftools&rut=deadbeef",
                "LangChain4j tools", "the wrapped one")
                + resultBlock("", "https://example.org/plain", "Plain", "the direct one")));

        WebSearchResults results = engine.search(QUERY);

        assertThat(results.results()).extracting(hit -> hit.url().toString())
                .containsExactly("https://docs.langchain4j.dev/tools", "https://example.org/plain");
    }

    // R-WEB-10
    @Test
    void engineNormalizesSchemeRelativeUrls() {
        serve(200, page(resultBlock("", "//cdn.example.com/page.html", "Scheme relative", "snippet")));

        assertThat(engine.search(QUERY).results())
                .extracting(hit -> hit.url().toString())
                .containsExactly("https://cdn.example.com/page.html");
    }

    // R-WEB-10
    @Test
    void engineSkipsAdResults() {
        serve(200, page(resultBlock("", "https://example.org/1", "Organic one", "snippet")
                + resultBlock(" result--ad", "https://ads.example.org/click", "Sponsored", "buy now")
                + resultBlock("", "https://example.org/2", "Organic two", "snippet")));

        WebSearchResults results = engine.search(QUERY);

        assertThat(results.results()).extracting(hit -> hit.title())
                .containsExactly("Organic one", "Organic two");
        assertThat(results.searchInformation().totalResults()).isEqualTo(2L);
    }

    // R-WEB-10
    @Test
    void parserSkipsIncompleteHits() {
        serve(200, page(resultBlock("", "https://example.org/complete", "Complete", "snippet")
                + resultBlock("", null, "No link at all", "snippet")
                + resultBlock("", "/relative/only", "Relative target", "snippet")
                + resultBlock("", "https://example.org/blank", "   ", "snippet")));

        WebSearchResults results = engine.search(QUERY);

        assertThat(results.results()).extracting(hit -> hit.url().toString())
                .containsExactly("https://example.org/complete");
        assertThat(results.searchInformation().totalResults()).isEqualTo(1L);
    }

    // R-WEB-10
    @Test
    void engineAppliesRequestedMaxResults() {
        serve(200, hitsPage(5));

        // GIVEN the request asks for fewer hits than the page has WHEN searching
        WebSearchResults limited = engine.search(WebSearchRequest.builder()
                .searchTerms(QUERY).maxResults(2).build());

        // THEN the request limit is honored
        assertThat(limited.results()).hasSize(2);

        // AND without a limit the engine itself does not truncate — capping is the tool's job
        assertThat(engine.search(QUERY).results()).hasSize(5);
    }

    // R-WEB-10
    @Test
    void engineReportsTotalBeforeTruncation() {
        serve(200, hitsPage(12));

        WebSearchResults results = engine.search(WebSearchRequest.builder()
                .searchTerms(QUERY).maxResults(5).build());

        // THEN M stays the page's hit count, N the truncated one — the basis of "showing N of M"
        assertThat(results.results()).hasSize(5);
        assertThat(results.searchInformation().totalResults()).isEqualTo(12L);
    }

    // R-WEB-12
    @Test
    void engineReportsZeroResultsForNoResultsPage() {
        serve(200, page("<h1>No results found for &quot;zzzqqq&quot;</h1>"));

        WebSearchResults results = engine.search("zzzqqq");

        assertThat(results.results()).isEmpty();
        assertThat(results.searchInformation().totalResults()).isZero();
    }

    // R-WEB-12
    @Test
    void engineReportsRateLimitAsError() {
        serve("/throttled", 429, page("<div>slow down</div>"));

        assertThatThrownBy(() -> engineAt("/throttled").search(QUERY))
                .isInstanceOf(WebSearchException.class)
                .hasMessageContaining("rate limit")
                .hasMessageContaining("HTTP 429");
    }

    // R-WEB-12
    @Test
    void engineReportsAcceptedButBlockedAsError() {
        serve("/bot-check-202", 202, page("<div>challenge</div>"));

        assertThatThrownBy(() -> engineAt("/bot-check-202").search(QUERY))
                .isInstanceOf(WebSearchException.class)
                .hasMessageContaining("rate limit")
                .hasMessageContaining("HTTP 202");
    }

    // R-WEB-12
    @Test
    void engineReportsServerError() {
        serve(500, page("<div>boom</div>"));

        assertThatThrownBy(() -> engine.search(QUERY))
                .isInstanceOf(WebSearchException.class)
                .hasMessageContaining("request failed (HTTP 500)");
    }

    // R-WEB-12
    @Test
    void engineReportsBrokenRedirectAsError() {
        serve(302, ""); // no Location — nothing to follow

        // THEN classified by class and cause context — the JDK's own wording of the redirect failure
        // is not ours to pin, it changes with the JDK
        assertThatThrownBy(() -> engine.search(QUERY))
                .isInstanceOf(WebSearchException.class)
                .hasMessageContaining("network")
                .hasCauseInstanceOf(IOException.class);
    }

    // R-WEB-12
    @Test
    void engineReportsRedirectLoopAsError() {
        server.createContext("/loop", exchange -> {
            exchange.getResponseHeaders().add("Location", "/loop");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        assertThatThrownBy(() -> engineAt("/loop").search(QUERY))
                .isInstanceOf(WebSearchException.class)
                .hasMessageContaining("unexpected response (HTTP 302)");
    }

    // R-WEB-12
    @Test
    void engineTreatsUnknownLayoutAsError() {
        serve(200, page("<div class=\"cards\"><span>something else entirely</span></div>"));

        assertThatThrownBy(() -> engine.search(QUERY))
                .isInstanceOf(WebSearchException.class)
                .hasMessageContaining("layout not recognized");
    }

    // R-WEB-12
    @Test
    void engineTreatsBotCheckPageAsError() {
        // GIVEN the challenge markup exactly as the live endpoint serves it (verified 2026-10-23): BEM
        // classes plus data-testid, no bare `.anomaly-modal` class, "anomaly" also in the form action —
        // and a no-results marker on the same page, so the bot check must still win (R-WEB-12 order)
        serve(200, page("""
                <form id="challenge-form" action="//duckduckgo.com/anomaly.js?q=eclipse+peon+ai" method="POST">
                  <div class="anomaly-modal__mask"><div class="anomaly-modal__modal" data-testid="anomaly-modal">
                    <div class="anomaly-modal__title">Unfortunately, bots use DuckDuckGo too.</div>
                  </div></div>
                </form>
                <div class="no-results">No results found</div>"""));

        assertThatThrownBy(() -> engine.search(QUERY))
                .isInstanceOf(WebSearchException.class)
                .hasMessageContaining("bot-check page");
    }

    // R-WEB-12
    @Test
    void zeroResultsPageEchoingAnomalyQueryStaysEmpty() {
        // GIVEN an honest zero whose echoed query contains the word "anomaly"
        serve(200, page("<div class=\"no-results\">No results for <b>anomaly detection</b></div>"));

        // WHEN
        WebSearchResults results = engine.search("anomaly detection");

        // THEN the zero stays a zero — a word the page echoes back is not a bot check
        assertThat(results.results()).isEmpty();
        assertThat(results.searchInformation().totalResults()).isZero();
    }

    // R-WEB-12
    @Test
    void engineReportsEmptyBodyAsError() {
        // GIVEN a 2xx answer with nothing in it WHEN searched THEN the named cause is the empty
        // answer, not a layout we failed to read
        serve(200, "");

        assertThatThrownBy(() -> engine.search(QUERY))
                .isInstanceOf(WebSearchException.class)
                .hasMessageContaining("empty response (HTTP 200)")
                .hasMessageNotContaining("layout");
    }

    // R-WEB-12
    @Test
    void engineReportsNetworkError() throws IOException {
        int deadPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            deadPort = socket.getLocalPort();
        }

        assertThatThrownBy(() -> new DuckDuckGoWebSearchEngine("http://localhost:" + deadPort + "/html").search(QUERY))
                .isInstanceOf(WebSearchException.class)
                .hasMessageContaining("network");
    }

    // R-WEB-12
    @Test
    void engineRestoresInterruptFlag() {
        serve(200, hitsPage(2));

        // GIVEN an already interrupted caller thread WHEN the engine sends its request
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> engine.search(QUERY))
                    .isInstanceOf(WebSearchException.class)
                    .hasMessage("interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private static String hitsPage(int count) {
        StringBuilder results = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            results.append(resultBlock("", "https://example.org/" + i, "Title " + i, "Snippet " + i));
        }
        return page(results.toString());
    }

    private static String resultBlock(String extraClass, String href, String title, String snippet) {
        return "<div class=\"result results_links web-result" + extraClass + "\">"
                + (href == null ? "" : "<a class=\"result__a\" href=\"" + href + "\">" + title + "</a>")
                + "<div class=\"result__body\">"
                + (snippet == null ? "" : "<a class=\"result__snippet\">" + snippet + "</a>")
                + "</div></div>";
    }

    private static String page(String body) {
        return "<html><head><title>DuckDuckGo</title></head><body>" + body + "</body></html>";
    }
}
