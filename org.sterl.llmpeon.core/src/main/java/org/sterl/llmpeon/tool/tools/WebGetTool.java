package org.sterl.llmpeon.tool.tools;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.sterl.llmpeon.shared.AiMonitor.AiFileUpdate;
import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.shared.QualifiedPathValidator;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * Downloads a URL to a file on disk. No size limit — the content belongs on disk,
 * the context only ever sees metadata (status, size, path).
 */
public class WebGetTool extends AbstractTool {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;

    public WebGetTool() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public boolean isEditTool() { return true; }

    @Tool("Download a URL to a file on disk. Absolute path required; overwrites existing. Returns status, size and path — never the content.")
    public String webGet(@P(name = "url") String url,
                         @P(name = "path", description = "absolute disk path to write to") String path)
            throws IOException, InterruptedException {
        ArgsUtil.requireNonBlank(url, "url");
        ArgsUtil.requireNonBlank(path, "path");
        QualifiedPathValidator.requireQualifiedDisk("webGet", path);
        validateWrite(path);

        Path target = Path.of(path).toAbsolutePath().normalize();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(DEFAULT_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        byte[] body = response.body();
        if (response.statusCode() >= 400)
            throw new IllegalArgumentException("webGet failed: HTTP " + response.statusCode() + " from " + url);

        boolean existed = Files.exists(target);
        String oldContent = existed ? Files.readString(target) : "";
        if (target.getParent() != null) Files.createDirectories(target.getParent());
        Files.write(target, body);
        if (existed)
            monitor.onFileUpdate(new AiFileUpdate(target.toString(), oldContent, new String(body, StandardCharsets.UTF_8)));
        onTool("Downloaded " + target + " (from " + url + ")");

        return body.length == 0
                ? "Downloaded " + target + ": HTTP " + response.statusCode()
                        + ", 0 bytes — server returned an empty body (from " + url + ")"
                : "Downloaded " + target + ": HTTP " + response.statusCode()
                        + ", " + body.length + " bytes (from " + url + ")";
    }
}
