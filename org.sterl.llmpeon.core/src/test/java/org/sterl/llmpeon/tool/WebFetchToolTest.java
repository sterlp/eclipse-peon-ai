package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sterl.llmpeon.tool.tools.WebFetchTool;

import com.sun.net.httpserver.HttpServer;

/**
 * webFetchAsMarkdown pagination (docs/web-tools.md R-WEB-1…3): first window, cache hit
 * without refetch, LRU eviction, honest HTTP error path.
 */
@Timeout(30)
class WebFetchToolTest {

    /** Flexmark appends one trailing empty line, so markers + 1 = markdown lines. */
    private static final int PAGE_3000_MARKERS = 2999;
    private static final int PAGE_1000_MARKERS = 999;

    WebFetchTool tool;
    HttpServer server;
    String base;
    Map<String, AtomicInteger> callCounters = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() throws IOException {
        tool = new WebFetchTool();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        base = "http://localhost:" + server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void serve(String context, int status, byte[] body) {
        server.createContext(context, exchange -> {
            callCounters.computeIfAbsent(context, k -> new AtomicInteger()).incrementAndGet();
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                try {
                    exchange.getResponseBody().write(body);
                } catch (IOException ignored) {
                    // test server — client already got the headers
                }
            }
            exchange.close();
        });
    }

    private int calls(String context) {
        return callCounters.getOrDefault(context, new AtomicInteger()).get();
    }

    // UC-WEB-1
    @Test
    void webFetchFirstCallReturnsFirstWindow() throws Exception {
        String url = base + "/page3000";
        serve("/page3000", 200, page(PAGE_3000_MARKERS).getBytes(StandardCharsets.UTF_8));

        // GIVEN a 3000-line markdown page WHEN webFetchAsMarkdown(url) without range
        String result = tool.webFetchAsMarkdown(url, null, null);

        // THEN lines 1-500 numbered, exact disclosure, line 501 not included
        assertThat(result)
                .startsWith("   1: LN1")
                .contains("500: LN500")
                .contains("lines 1–500 of 3000 — read on with startLine=501")
                .doesNotContain("501: LN501");

        // AND a window request beyond the hard max is clamped to 500 lines
        String clamped = tool.webFetchAsMarkdown(url, 1, 3000);
        assertThat(clamped)
                .contains("500: LN500")
                .contains("lines 1–500 of 3000 — read on with startLine=501")
                .doesNotContain("501: LN501");

        // AND endLine < startLine is swapped like FileLines.extract — the label names the shown lines
        String swapped = tool.webFetchAsMarkdown(url, 500, 100);
        assertThat(swapped)
                .startsWith(" 100: LN100")
                .contains("500: LN500")
                .contains("lines 100–500 of 3000 — read on with startLine=501")
                .doesNotContain("501: LN501");

        // AND a start beyond the document is reported honestly
        assertThat(tool.webFetchAsMarkdown(url, 3001, null))
                .isEqualTo("URL has 3000 lines, requested start 3001");
    }

    // UC-WEB-2
    @Test
    void webFetchCacheHitPaginatesWithoutRefetch() throws Exception {
        String url = base + "/page3000";
        serve("/page3000", 200, page(PAGE_3000_MARKERS).getBytes(StandardCharsets.UTF_8));
        tool.webFetchAsMarkdown(url, null, null);

        // GIVEN the URL is cached WHEN webFetchAsMarkdown(url, startLine=501)
        String result = tool.webFetchAsMarkdown(url, 501, null);

        // THEN lines 501-1000 from the cached snapshot, no second HTTP call
        assertThat(result)
                .startsWith(" 501: LN501")
                .contains("1000: LN1000")
                .contains("lines 501–1000 of 3000 — read on with startLine=1001")
                .doesNotContain("1001: LN1001");
        assertThat(calls("/page3000")).isEqualTo(1);
    }

    // UC-WEB-3
    @Test
    void webFetchCacheEvictionRefetches() throws Exception {
        for (int i = 1; i <= 6; i++) {
            String url = base + "/page" + i;
            serve("/page" + i, 200, page(PAGE_1000_MARKERS).getBytes(StandardCharsets.UTF_8));
            tool.webFetchAsMarkdown(url, null, null);
        }
        assertThat(calls("/page1")).isEqualTo(1);

        // GIVEN the 6th URL evicted the oldest WHEN the evicted URL is requested again
        String result = tool.webFetchAsMarkdown(base + "/page1", null, null);

        // THEN refetch, window back to 1-500
        assertThat(result)
                .startsWith("   1: LN1")
                .contains("lines 1–500 of 1000 — read on with startLine=501");
        assertThat(calls("/page1")).isEqualTo(2);
    }

    // UC-WEB-4
    @Test
    void webFetchHttpErrorReturnsStatusAndSnippet() throws Exception {
        StringBuilder body = new StringBuilder("<html><body>");
        for (int i = 1; i <= 50; i++) {
            if (i > 1) body.append("<br>");
            body.append("ERR").append(i);
        }
        body.append("-ENDBODY</body></html>");
        String url = base + "/error";
        serve("/error", 500, body.toString().getBytes(StandardCharsets.UTF_8));

        // GIVEN an HTTP 500 with a long body WHEN webFetchAsMarkdown(url)
        String result = tool.webFetchAsMarkdown(url, null, null);

        // THEN status plus snippet of the first lines, never the full body
        assertThat(result)
                .startsWith("Failed to fetch " + url + ". HTTP status 500")
                .contains("1: ERR1")
                .contains("10: ERR10")
                // snippet is capped at 10 lines — line 11 of the error markdown must not leak
                .doesNotContain("11: ERR11")
                .doesNotContain("ERR50-ENDBODY");

        // AND error responses are never cached — a retry hits the server again
        assertThat(tool.webFetchAsMarkdown(url, null, null)).contains("HTTP status 500");
        assertThat(calls("/error")).isEqualTo(2);
    }

    @Test
    void webFetchEmptyContentIsHonest() throws Exception {
        String url = base + "/empty";
        serve("/empty", 200, new byte[0]);

        assertThat(tool.webFetchAsMarkdown(url, null, null))
                .isEqualTo("Fetched " + url + ": empty content (0 lines)");
    }

    private static String page(int markers) {
        StringBuilder sb = new StringBuilder("<html><body>");
        for (int i = 1; i <= markers; i++) {
            if (i > 1) sb.append("<br>");
            sb.append("LN").append(i);
        }
        sb.append("</body></html>");
        return sb.toString();
    }
}
