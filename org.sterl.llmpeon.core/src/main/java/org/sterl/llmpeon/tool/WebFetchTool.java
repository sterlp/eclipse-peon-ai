package org.sterl.llmpeon.tool;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * Simple tool to fetch a website and convert its content to Markdown.
 */
public class WebFetchTool extends AbstractTool {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern CHARSET_PATTERN = Pattern.compile("charset=([^;\\s]+)", Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient;
    private final FlexmarkHtmlConverter htmlToMarkdownConverter;

    public WebFetchTool() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.htmlToMarkdownConverter = FlexmarkHtmlConverter.builder().build();
    }

    /**
     * Fetches the content of the given URL and converts it to Markdown.
     * @param url the URL to fetch
     * @return the Markdown content
     * @throws IOException if a network error occurs
     * @throws InterruptedException if the request is interrupted
     */
    @Tool("""
          Fetches the content of the given URL and converts it to Markdown. USe it if you should read an URL.
          e.g. read maven langchain version https://central.sonatype.com/artifact/<groupId>/<artifactId>
          """)
    public String fetchAsMarkdown(@P("url the URL to fetch") String url) throws IOException, InterruptedException {
        if (url == null || url.isBlank()) {
            return "URL cannot be null or empty";
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(DEFAULT_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .GET()
                .build();

        monitorMessage("Fetching " + url);
        
        HttpResponse<byte[]> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        Charset charset = extractCharset(response).orElse(StandardCharsets.UTF_8);
        String htmlContent = new String(response.body(), charset);
        
        if (response.statusCode() >= 400) {
            return "Failed to fetch " + url + ". HTTP status code: " + response.statusCode()
                + " Response: " + htmlContent;
        }

        return "Content of " + url + "\n" 
            + this.htmlToMarkdownConverter.convert(htmlContent);
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
