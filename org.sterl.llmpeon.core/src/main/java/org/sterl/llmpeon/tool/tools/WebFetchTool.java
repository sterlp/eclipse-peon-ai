package org.sterl.llmpeon.tool.tools;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.shared.FileLines;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * Simple tool to fetch a website and convert its content to Markdown.
 * Results are cached per URL (LRU, {@value #CACHE_MAX_URLS} entries) so that
 * pagination over the same URL is stable and does not refetch.
 */
public class WebFetchTool extends AbstractTool {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern CHARSET_PATTERN = Pattern.compile("charset=([^;\\s]+)", Pattern.CASE_INSENSITIVE);

    static final int CACHE_MAX_URLS = 5;
    static final int MAX_WINDOW_LINES = 500;
    static final int SNIPPET_LINES = 10;

    private final HttpClient httpClient;
    private final FlexmarkHtmlConverter htmlToMarkdownConverter;
    private final Map<String, String> markdownCache = new LinkedHashMap<>() {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > CACHE_MAX_URLS;
        }
    };

    public WebFetchTool() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.htmlToMarkdownConverter = FlexmarkHtmlConverter.builder().build();
    }

    /**
     * Fetches the content of the given URL, converts it to Markdown and returns a
     * numbered line window (max {@value #MAX_WINDOW_LINES} lines) with a disclosure.
     *
     * @param url the URL to fetch
     * @param startLine first line of the window (1-based); null/0 = start of the document
     * @param endLine last line of the window (1-based); null/0 = window default (clamped to {@value #MAX_WINDOW_LINES})
     * @return numbered lines plus disclosure, or an honest error message
     */
    @Tool("Fetch a URL as cached Markdown. startLine/endLine (1-based, 0 = default) page the result; max 500 lines per call, disclosed. HTTP 4xx/5xx: status plus snippet.")
    public String webFetchAsMarkdown(@P(name = "url") String url,
                                     @P(name = "startLine", description = "first line, 1-based; 0 = start of document", required = false) Integer startLine,
                                     @P(name = "endLine", description = "last line, 1-based; 0 = window default", required = false) Integer endLine)
            throws IOException, InterruptedException {
        ArgsUtil.requireNonBlank(url, "url");

        FetchResult fetched = fetch(url);
        if (fetched.status() >= 400) {
            // R-WEB-3: status plus snippet of the first lines, never the full body
            return "Failed to fetch " + url + ". HTTP status " + fetched.status()
                    + System.lineSeparator() + FileLines.extract(fetched.markdown(), 1, SNIPPET_LINES);
        }
        String markdown = fetched.markdown();
        if (markdown.isBlank()) {
            return "Fetched " + url + ": empty content (0 lines)";
        }

        int total = FileLines.countLines(markdown);
        int start = startLine == null || startLine <= 0 ? 1 : startLine;
        if (start > total) {
            return "URL has " + total + " lines, requested start " + start;
        }
        int windowEnd = start + MAX_WINDOW_LINES - 1;
        int end = endLine == null || endLine <= 0 ? windowEnd : Math.min(endLine, windowEnd);
        int shownEnd = Math.min(end, total);

        StringBuilder result = new StringBuilder(FileLines.extract(markdown, start, shownEnd));
        result.append("lines ").append(start).append('\u2013').append(shownEnd).append(" of ").append(total);
        if (shownEnd < total) {
            result.append(" — read on with startLine=").append(shownEnd + 1);
        }
        return result.toString();
    }

    /**
     * Returns the cached Markdown for the URL, or fetches and caches it.
     * HTTP >= 400 is returned as a result (never cached), not thrown.
     */
    private FetchResult fetch(String url) throws IOException, InterruptedException {
        synchronized (markdownCache) {
            String cached = markdownCache.get(url);
            if (cached != null) {
                return new FetchResult(200, cached);
            }
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(DEFAULT_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .GET()
                .build();

        HttpResponse<byte[]> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        Charset charset = extractCharset(response).orElse(StandardCharsets.UTF_8);
        String markdown = this.htmlToMarkdownConverter.convert(new String(response.body(), charset));

        if (response.statusCode() >= 400) {
            onProblem("Failed to fetch " + url + " - " + response.statusCode());
            return new FetchResult(response.statusCode(), markdown);
        }

        onTool("Reading " + url);
        synchronized (markdownCache) {
            markdownCache.put(url, markdown);
        }
        return new FetchResult(response.statusCode(), markdown);
    }

    private record FetchResult(int status, String markdown) {
    }

    private Optional<Charset> extractCharset(HttpResponse<?> response) {
        return response.headers()
                .firstValue("Content-Type")
                .flatMap(contentType -> {
                    Matcher matcher = CHARSET_PATTERN.matcher(contentType);
                    if (matcher.find()) {
                        String charsetName = matcher.group(1);
                        try {
                            return Optional.of(Charset.forName(charsetName));
                        } catch (Exception e) {
                            return Optional.empty();
                        }
                    }
                    return Optional.empty();
                });
    }
}
