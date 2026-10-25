package org.sterl.llmpeon.tool.web;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.shared.BoundedLruCache;
import org.sterl.llmpeon.shared.FileLines;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.tools.AbstractTool;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * Fetches a URL and returns a page of its text. Only HTML is converted to Markdown — every other
 * content type reaches the agent as the raw text the server sent, so a JSON API or a plain-text
 * file is not silently rewritten (R-WEB-13). The footer of the result names which path ran.
 * <p>
 * Results are cached per URL (LRU, {@value #CACHE_MAX_URLS} entries) together with the path they
 * took, so pagination over the same URL is stable, never refetches and never reconverts.
 */
public class WebFetchTool extends AbstractTool {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern CHARSET_PATTERN = Pattern.compile("charset=([^;\\s]+)", Pattern.CASE_INSENSITIVE);

    /** Snapshot-cache cap — R-WEB-9 (org.sterl.llmpeon.core/docs/web-tools.md): 16 pages, named constant, no user config. */
    static final int CACHE_MAX_URLS = 16;
    static final int MAX_WINDOW_LINES = 500;
    static final int SNIPPET_LINES = 10;

    private final HttpClient httpClient;
    private final FlexmarkHtmlConverter htmlToMarkdownConverter;
    private final BoundedLruCache<String, FetchedPage> pageCache = new BoundedLruCache<>(CACHE_MAX_URLS);

    public WebFetchTool() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.htmlToMarkdownConverter = FlexmarkHtmlConverter.builder().build();
    }

    /**
     * Fetches the content of the given URL and returns a numbered line window (max
     * {@value #MAX_WINDOW_LINES} lines) with a disclosure that also names the path the content
     * took: HTML arrives converted to Markdown, anything else as raw text (R-WEB-13).
     *
     * @param url the URL to fetch
     * @param startLine first line of the window (1-based); null/0 = start of the document
     * @param endLine last line of the window (1-based); null/0 = window default (clamped to {@value #MAX_WINDOW_LINES})
     * @return numbered lines plus disclosure, or an honest error message
     */
    @Tool("Fetch a URL as cached text, paginated. HTML is converted to Markdown; any other content type (text/plain, JSON, …) is returned as raw text — the footer names which. startLine/endLine (1-based, 0 = default) page the result; max 500 lines per call, disclosed. HTTP 4xx/5xx: status plus snippet.")
    public String webFetchAsMarkdown(@P(name = "url") String url,
                                     @P(name = "startLine", description = "first line, 1-based; 0 = start of document", required = false) Integer startLine,
                                     @P(name = "endLine", description = "last line, 1-based; 0 = window default", required = false) Integer endLine)
            throws IOException, InterruptedException {
        ArgsUtil.requireNonBlank(url, "url");

        FetchResult fetched = fetch(url);
        if (fetched.status() >= 400) {
            // R-WEB-3: status plus snippet of the first lines, never the full body
            return "Failed to fetch " + url + ". HTTP status " + fetched.status()
                    + System.lineSeparator() + FileLines.extract(fetched.page().text(), 1, SNIPPET_LINES);
        }
        FetchedPage page = fetched.page();
        String text = page.text();
        if (text.isBlank()) {
            return "Fetched " + url + ": empty content (0 lines)";
        }

        int total = FileLines.countLines(text);
        int start = startLine == null || startLine <= 0 ? 1 : startLine;
        int end = endLine == null ? 0 : endLine;
        if (end > 0 && end < start) {
            // same swap semantics as FileLines.extract — the label below always names the shown lines
            int swap = start;
            start = end;
            end = swap;
        }
        int windowEnd = start + MAX_WINDOW_LINES - 1;
        end = end <= 0 ? windowEnd : Math.min(end, windowEnd);
        if (start > total) {
            return "URL has " + total + " lines, requested start " + start;
        }
        int shownEnd = Math.min(end, total);

        StringBuilder builder = new StringBuilder(FileLines.extract(text, start, shownEnd));
        // the label qualifies the content, the read-on hint stays the last token
        builder.append("lines ").append(start).append('\u2013').append(shownEnd).append(" of ").append(total)
                .append(" (").append(page.pathLabel()).append(')');
        if (shownEnd < total) {
            builder.append(" — read on with startLine=").append(shownEnd + 1);
        }
        String result = builder.toString();
        onTool("Fetched " + url + " " + StringUtil.charsSuffix(result));
        return result;
    }

    /**
     * Returns the cached page for the URL, or fetches and caches it. Text and path label travel
     * together, so a hit re-reads no header and runs no converter (R-WEB-13).
     * HTTP >= 400 is returned as a result (never cached), not thrown.
     */
    private FetchResult fetch(String url) throws IOException, InterruptedException {
        FetchedPage cached = pageCache.get(url);
        if (cached != null) {
            return new FetchResult(200, cached);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(DEFAULT_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .GET()
                .build();

        HttpResponse<byte[]> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        ContentType contentType = ContentType.of(response.headers().firstValue("Content-Type").orElse(null));
        String body = new String(response.body(), contentType.charsetOrUtf8());
        FetchedPage page = contentType.isHtml()
                ? new FetchedPage(this.htmlToMarkdownConverter.convert(body), true)
                : new FetchedPage(body, false);

        if (response.statusCode() >= 400) {
            onProblem("Failed to fetch " + url + " - " + response.statusCode());
            return new FetchResult(response.statusCode(), page);
        }

        onTool("Reading " + url);
        pageCache.put(url, page);
        return new FetchResult(response.statusCode(), page);
    }

    private record FetchResult(int status, FetchedPage page) {
    }

    /** One fetched page as the caller receives it: the text and how it was produced (R-WEB-13). */
    private record FetchedPage(String text, boolean convertedFromHtml) {

        String pathLabel() {
            return convertedFromHtml ? "converted from HTML" : "raw text";
        }
    }

    /**
     * The response's Content-Type, parsed once: the media type decides the path, the charset
     * decodes the body — one header, one parse, no chance for the two to diverge.
     */
    private record ContentType(String mediaType, @Nullable Charset charset) {

        static ContentType of(@Nullable String header) {
            if (header == null) return new ContentType("", null);
            int parameters = header.indexOf(';');
            String mediaType = (parameters < 0 ? header : header.substring(0, parameters))
                    .strip().toLowerCase(Locale.ROOT);
            return new ContentType(mediaType, extractCharset(header));
        }

        /** {@code text/html} and any {@code +xml} descendant — everything else stays raw text (R-WEB-13). */
        boolean isHtml() {
            return mediaType.equals("text/html") || mediaType.endsWith("+xml");
        }

        Charset charsetOrUtf8() {
            return charset != null ? charset : StandardCharsets.UTF_8;
        }

        private static @Nullable Charset extractCharset(String header) {
            Matcher matcher = CHARSET_PATTERN.matcher(header);
            if (!matcher.find()) return null;
            try {
                return Charset.forName(matcher.group(1));
            } catch (Exception unsupportedOrUnknownName) {
                return null;
            }
        }
    }
}
