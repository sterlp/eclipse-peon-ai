package org.sterl.llmpeon.tool.web;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.tools.AbstractTool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;

/**
 * {@code webSearch} — a numbered hit list (title, URL, snippet) in the context, never page content
 * (org.sterl.llmpeon.core/docs/web-tools.md R-WEB-10/R-WEB-12). Reading a hit stays an explicit
 * {@link WebFetchTool#webFetchAsMarkdown} call, so every page enters the context through its
 * pagination and cache.
 * <p>
 * The tool owns the caps, the rendering and the error wording; the transport lives behind the
 * langchain4j {@link WebSearchEngine} contract (docs/adr/0074-websearch-eigener-ddg-client.md),
 * so a swap is one constructor argument. The cap is applied here <em>authoritatively</em> — the
 * value sent to the engine is only a hint, a foreign engine can never push more than
 * {@value #HARD_MAX_RESULTS} hits into the context. A rate-limited failure keeps its error wording
 * and gains the way forward — the tool never retries on its own (R-WEB-14).
 */
public class WebSearchTool extends AbstractTool {

    /** R-WEB-10: 8 hits per call by default, {@code maxResults} overrides up to a hard 10. */
    static final int DEFAULT_MAX_RESULTS = 8;
    static final int HARD_MAX_RESULTS = 10;
    static final int SNIPPET_MAX_CHARS = 200;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * R-WEB-14: the engine names the cause (DuckDuckGoWebSearchEngine#bodyOrThrow); the guidance rides
     * on that name. Case-insensitive — a foreign engine writes "Rate Limit", and a cause we only
     * recognise in one spelling would drop the way forward without a word.
     */
    private static final Pattern RATE_LIMIT = Pattern.compile("rate limit|bot check|HTTP (202|429)", Pattern.CASE_INSENSITIVE);

    /** R-WEB-14: no retry — the observed block outlasted every immediate retry and lasted minutes. */
    static final String RATE_LIMIT_GUIDANCE = "Do not retry immediately: a DuckDuckGo block lasts minutes, "
            + "and repeat calls make it worse. Either continue this turn without searching, "
            + "or read a known URL directly with webFetchAsMarkdown.";

    private final WebSearchEngine engine;

    public WebSearchTool() {
        this(new DuckDuckGoWebSearchEngine());
    }

    /** Test seam and engine swap point — everything above the transport is engine independent. */
    public WebSearchTool(WebSearchEngine engine) {
        this.engine = engine;
    }

    /**
     * @param query      the search terms
     * @param maxResults hits to return (1…10); null/0/negative = {@value #DEFAULT_MAX_RESULTS}
     * @return numbered hit list, an honest "0 results" line, or an honest failure line — never an
     *         empty body, and never a partial result presented as the whole truth
     */
    @Tool("Search the web via DuckDuckGo. Returns a numbered hit list (title, URL, snippet) — "
            + "max 8 results (maxResults overrides, hard max 10), disclosed when capped. "
            + "Page content is not included: read a hit with webFetchAsMarkdown. "
            + "Rate-limited: the failure says why; do not retry in the same turn.")
    public String webSearch(@P(name = "query") String query,
            @P(name = "maxResults", description = "hits to return, 1-10; default 8", required = false) Integer maxResults) {
        ArgsUtil.requireNonBlank(query, "query");

        int limit = effectiveLimit(maxResults);
        WebSearchResults results;
        try {
            results = engine.search(WebSearchRequest.builder()
                    .searchTerms(query)
                    .maxResults(limit)
                    .build());
        } catch (WebSearchException failed) {
            // R-WEB-12: a failure must stay a failure — "0 results" would tell the agent the topic has no hits
            onProblem("webSearch failed for '" + query + "' - " + failed.getMessage());
            return failureLine(query, failed.getMessage());
        }

        List<WebSearchOrganicResult> returned = results.results();
        List<WebSearchOrganicResult> shown = returned.stream()
                .filter(hit -> isLinkable(hit.url()))
                .limit(limit)
                .toList();

        String result = shown.isEmpty() //
                ? zeroResults(query, returned.size())
                : render(shown, honestTotal(results), maxResults);
        onTool("Searched '" + query + "': " + shown.size() + " results " + StringUtil.charsSuffix(result));
        return result;
    }

    /** R-WEB-12 tail always; R-WEB-14 adds the way forward — only for a rate limit / bot check. */
    private static String failureLine(String query, String cause) {
        String line = "webSearch failed for '" + query + "': " + cause
                + ". No results were returned — this is an error, not an empty result set.";
        return isRateLimit(cause) ? line + " " + RATE_LIMIT_GUIDANCE : line;
    }

    /** An engine may name nothing at all (ADR-0074 keeps the swap open) — a null cause stays honest, it just gets no guidance. */
    private static boolean isRateLimit(String cause) {
        return cause != null && RATE_LIMIT.matcher(cause).find();
    }

    /** The numbered blocks, then only the disclosures that apply — each on its own line. */
    private static String render(List<WebSearchOrganicResult> shown, long total, Integer requested) {
        List<String> lines = new ArrayList<>();
        boolean snippetTruncated = false;
        for (int i = 0; i < shown.size(); i++) {
            WebSearchOrganicResult hit = shown.get(i);
            lines.add((i + 1) + ". " + oneLine(hit.title()));
            lines.add("   " + hit.url());
            String snippet = oneLineOrNull(hit.snippet());
            if (snippet != null) {
                String limited = truncate(snippet, SNIPPET_MAX_CHARS);
                snippetTruncated |= limited.length() < snippet.length();
                lines.add("   " + limited);
            }
        }
        if (requested != null && requested > HARD_MAX_RESULTS) {
            lines.add("maxResults capped at " + HARD_MAX_RESULTS + " (requested " + requested + ")");
        }
        if (shown.size() < total) {
            lines.add("showing " + shown.size() + " of " + total + " results");
        }
        // AGENTS.md: a cut the agent cannot see is a silent limit — the ellipsis alone is not a name
        if (snippetTruncated) {
            lines.add("snippets truncated to " + SNIPPET_MAX_CHARS + " characters");
        }
        return String.join(System.lineSeparator(), lines);
    }

    private static int effectiveLimit(Integer requested) {
        if (requested == null || requested <= 0) return DEFAULT_MAX_RESULTS;
        return Math.min(requested, HARD_MAX_RESULTS);
    }

    /**
     * M of "showing N of M": what the engine says it found, never less than the hits it handed over —
     * an engine that truncated itself still discloses the bigger M, and one that under-reports its
     * total cannot shrink M below the list the agent is about to see.
     */
    private static long honestTotal(WebSearchResults results) {
        return Math.max(results.results().size(), results.searchInformation().totalResults());
    }

    /**
     * R-WEB-10 skip rule, reachable half: a hit without an absolute http(s) target cannot be read on
     * with {@code webFetchAsMarkdown}. The missing-title half of that rule is enforced by
     * {@link WebSearchOrganicResult} itself (blank titles are rejected by its constructor) and already
     * applied by the parser, so a guard here would never fire.
     */
    private static boolean isLinkable(URI url) {
        String scheme = url.getScheme();
        // a non-null scheme already makes the URI absolute (URI.isAbsolute()), so this is the whole check
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private static String zeroResults(String query, int unusableHits) {
        if (unusableHits == 0) {
            return "0 results for '" + query + "' (DuckDuckGo returned no hits; try a shorter or different query).";
        }
        return "0 results for '" + query + "' (no hit of " + unusableHits
                + " had an absolute http(s) URL — nothing usable was returned).";
    }

    /** Titles and snippets arrive as web content: one line each keeps the numbered block intact. */
    private static String oneLine(String value) {
        return WHITESPACE.matcher(value).replaceAll(" ").strip();
    }

    private static String oneLineOrNull(String value) {
        if (value == null) return null;
        String oneLine = oneLine(value);
        return oneLine.isEmpty() ? null : oneLine;
    }

    /** R-WEB-10: at most {@code maxChars} characters in total, the last one the ellipsis. */
    private static String truncate(String value, int maxChars) {
        if (value.length() <= maxChars) return value;
        return value.substring(0, maxChars - 1) + '…';
    }
}
