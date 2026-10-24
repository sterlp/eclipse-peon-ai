package org.sterl.llmpeon.tool.tools;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.sterl.llmpeon.shared.ArgsUtil;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;

/**
 * Thin DuckDuckGo client behind the langchain4j {@link WebSearchEngine} contract
 * (org.sterl.llmpeon.core/docs/web-tools.md R-WEB-10, docs/adr/0074-websearch-eigener-ddg-client.md):
 * the endpoint is free and key-less, and implementing the interface keeps a later engine swap a
 * one-liner in the tool.
 * <p>
 * Transport and honest classification only — parsing is {@link DuckDuckGoHtmlParser}, every failure
 * leaves as {@link WebSearchException} (R-WEB-12). Result caps, rendering and the "showing N of M"
 * disclosure belong to {@code WebSearchTool}: this class knows neither the agent prompt nor the UI.
 */
public class DuckDuckGoWebSearchEngine implements WebSearchEngine {

    static final String DEFAULT_ENDPOINT = "https://html.duckduckgo.com/html/";

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final String endpoint;
    private final HttpClient httpClient;

    public DuckDuckGoWebSearchEngine() {
        this(DEFAULT_ENDPOINT);
    }

    /** Test seam: a local server instead of DuckDuckGo, everything else identical. */
    public DuckDuckGoWebSearchEngine(String endpoint) {
        ArgsUtil.requireNonBlank(endpoint, "endpoint");
        this.endpoint = endpoint;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    /**
     * {@code totalResults} is the number of hits parsed from the page <em>before</em> the request's
     * {@code maxResults} truncation — that is what lets the tool say "showing N of M" honestly
     * instead of mistaking its own cap for the end of the result set.
     */
    @Override
    public WebSearchResults search(WebSearchRequest request) {
        ArgsUtil.requireNonNull(request, "request");

        List<DuckDuckGoHtmlParser.Hit> hits = DuckDuckGoHtmlParser.parse(fetch(request.searchTerms()));
        return WebSearchResults.from(
                WebSearchInformationResult.from((long) hits.size()),
                truncate(hits, request.maxResults()).stream()
                        .map(DuckDuckGoWebSearchEngine::toOrganicResult)
                        .toList());
    }

    private static List<DuckDuckGoHtmlParser.Hit> truncate(List<DuckDuckGoHtmlParser.Hit> hits, Integer maxResults) {
        if (maxResults == null || maxResults <= 0 || hits.size() <= maxResults) return hits;
        return hits.subList(0, maxResults);
    }

    private static WebSearchOrganicResult toOrganicResult(DuckDuckGoHtmlParser.Hit hit) {
        return WebSearchOrganicResult.from(hit.title(), hit.url(), hit.snippet(), null);
    }

    private String fetch(String searchTerms) {
        ArgsUtil.requireNonBlank(searchTerms, "searchTerms");

        HttpRequest request = HttpRequest.newBuilder(
                    URI.create(endpoint + "?q=" + URLEncoder.encode(searchTerms, StandardCharsets.UTF_8)))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        try {
            return bodyOrThrow(httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
        } catch (IOException network) {
            throw new WebSearchException("network error: " + network.getMessage(), network);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new WebSearchException("interrupted", interrupted);
        }
    }

    /** Rate limit and bot check are named as such — a swallowed 429 would read as "no results". */
    private static String bodyOrThrow(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status == 202 || status == 429)
            throw new WebSearchException("rejected (HTTP " + status + " — rate limit or bot check)");
        if (status >= 400) throw new WebSearchException("request failed (HTTP " + status + ")");
        if (status < 200 || status >= 300) throw new WebSearchException("unexpected response (HTTP " + status + ")");
        String body = response.body();
        // an empty answer is its own cause — calling it a layout drift would make the agent suspect
        // its own parser for a failure of the transport (R-WEB-12)
        if (body == null || body.isBlank()) throw new WebSearchException("empty response (HTTP " + status + ")");
        return body;
    }
}
