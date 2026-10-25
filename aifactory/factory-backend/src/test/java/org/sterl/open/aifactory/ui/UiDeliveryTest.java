package org.sterl.open.aifactory.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.NoOpResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 * Delivery is part of the increment, not of the deployment (R-WP-22): a UI that answers 500 in the
 * browser is not built, however green its unit tests are. MockMvc cannot prove this — the failure this
 * pins (a handler returning a null view name, resolved into an internal-resource forward onto its own
 * path) only surfaces as a {@code StackOverflowError} over real HTTP, so everything here runs against
 * a started server on a random port.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UiDeliveryTest {

    /** The hashed names change with every build, so they are read from the html, never hardcoded. */
    private static final Pattern ASSET = Pattern.compile("assets/(index-[\\w-]+\\.(?:js|css))");
    private static final String INDEX = "static/ui/index.html";

    @TempDir
    static Path home;

    @LocalServerPort
    int port;

    private RestTemplate http;

    @DynamicPropertySource
    static void isolateHome(DynamicPropertyRegistry registry) {
        registry.add("aifactory.home", () -> home.toString());
    }

    @BeforeEach
    void talkToTheRunningServer() {
        this.http = new RestTemplate();
        // the status IS the assertion — a client that throws on 4xx/5xx would hide what the app answers
        this.http.setErrorHandler(new NoOpResponseErrorHandler());
    }

    @Test
    // UC-WP-34
    void indexHtmlServedAtUiRoot() throws Exception {
        String built = read(INDEX);
        assertThat(built).contains("<div id=\"root\">");

        for (String path : List.of("/ui/", "/ui")) {
            ResponseEntity<String> res = http.getForEntity(url(path), String.class);

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(res.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_HTML);
            assertThat(res.getBody()).contains("<div id=\"root\">").isEqualTo(built);
        }

        // `/` may only redirect, and that is only visible to a client that does not follow it
        HttpResponse<String> root = sendNeverFollowingRedirects("/");
        assertThat(root.statusCode()).isEqualTo(HttpStatus.FOUND.value());
        assertThat(root.headers().firstValue("Location").orElse("")).endsWith("/ui/");
    }

    @Test
    // UC-WP-35
    void hashedAssetsServedAsThemselves() {
        List<String> assets = assetsOf(read(INDEX));
        assertThat(assets).anyMatch(name -> name.endsWith(".js")).anyMatch(name -> name.endsWith(".css"));

        for (String name : assets) {
            Resource file = new ClassPathResource("static/ui/assets/" + name);
            byte[] built = readBytes(file);
            assertThat(built).isNotEmpty();

            ResponseEntity<byte[]> res = http.getForEntity(url("/ui/assets/" + name), byte[].class);

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
            MediaType contentType = res.getHeaders().getContentType();
            assertThat(contentType).isNotNull();
            assertThat(contentType.toString())
                    .isEqualTo(name.endsWith(".js") ? "text/javascript" : "text/css");
            // the shape this increment is about: an asset answering with the API's error body
            assertThat(contentType.includes(MediaType.APPLICATION_JSON)).isFalse();
            assertThat(contentType.includes(MediaType.TEXT_PLAIN)).isFalse();

            byte[] body = res.getBody();
            assertThat(body).isNotNull().hasSize(built.length)
                    .startsWith(Arrays.copyOf(built, Math.min(32, built.length)));
            assertThat(res.getHeaders().getContentLength()).isEqualTo(built.length);
        }
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static List<String> assetsOf(String indexHtml) {
        List<String> names = new ArrayList<>();
        for (Matcher matcher = ASSET.matcher(indexHtml); matcher.find();) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static String read(String classpathLocation) {
        return new String(readBytes(new ClassPathResource(classpathLocation)), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(Resource resource) {
        try {
            return resource.getInputStream().readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("the built SPA is missing from the classpath: " + resource, e);
        }
    }

    private HttpResponse<String> sendNeverFollowingRedirects(String path) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url(path))).GET()
                .timeout(Duration.ofSeconds(10)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
