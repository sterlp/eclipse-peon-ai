package org.sterl.open.aifactory.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.NoOpResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import org.sterl.open.aifactory.FactorySpringTest;

/**
 * Delivery is part of the increment, not of the deployment (R-WP-22): a UI that answers 500 in the
 * browser is not built, however green its unit tests are. MockMvc cannot prove this — the failure this
 * pins (a handler returning a null view name, resolved into an internal-resource forward onto its own
 * path) only surfaces as a {@code StackOverflowError} over real HTTP, so everything here runs against
 * a started server on a random port.
 */
class UiDeliveryTest extends FactorySpringTest {

    /** The hashed names change with every build, so they are read from the html, never hardcoded. */
    private static final Pattern ASSET = Pattern.compile("assets/(index-[\\w-]+\\.(?:js|css))");
    private static final String INDEX = "static/ui/index.html";

    @LocalServerPort
    int port;

    private RestTemplate http;

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

    /**
     * An address of the client's own routing has no file behind it (UC-WP-43): the browser app is what the
     * server answers, so a deep link and a reload of it are the app and not a {@code 404}. Asserted over
     * real HTTP because the fallback is a resource handler, and MockMvc would hide the handler order that
     * keeps {@code /ui/assets/**} out of it.
     */
    @Test
    // UC-WP-43
    void deepLinksAnswerTheApp() {
        String built = read(INDEX);

        for (String path : List.of("/ui/chat/demo-1a2b3c4", "/ui/settings")) {
            ResponseEntity<String> res = http.getForEntity(url(path), String.class);

            assertThat(res.getStatusCode())
                    .as("%s is a route of the app, not a missing file", path)
                    .isEqualTo(HttpStatus.OK);
            assertThat(res.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_HTML);
            assertThat(res.getBody())
                    .as("%s answers the app itself, so the client can route it", path)
                    .isEqualTo(built);
        }
    }

    @Test
    // UC-WP-43
    void aReloadOfThePathAnswersTheApp() throws Exception {
        String path = "/ui/chat/demo-1a2b3c4";
        String opened = http.getForObject(url(path), String.class);

        // a reload is a plain GET at the same address — a client that follows redirects could not tell a
        // reload from a forward chain, so this one does not follow
        HttpResponse<String> reloaded = sendNeverFollowingRedirects(path);

        assertThat(reloaded.statusCode())
                .as("the answer is the app, not a redirect onto something else")
                .isEqualTo(HttpStatus.OK.value());
        assertThat(reloaded.headers().firstValue("Location"))
                .as("nothing redirects here, so no request can end in a chain of them (R-WP-22)")
                .isEmpty();
        assertThat(reloaded.body()).isEqualTo(opened).contains("<div id=\"root\">");
    }

    @Test
    // UC-WP-43
    void anUnknownPathServesTheApp() throws Exception {
        String path = "/ui/no/such/route";

        HttpResponse<String> first = sendNeverFollowingRedirects(path);
        HttpResponse<String> second = sendNeverFollowingRedirects(path);

        assertThat(first.statusCode())
                .as("an address nobody mapped is the app's problem to route, not a 500 and not a 404")
                .isEqualTo(HttpStatus.OK.value());
        assertThat(first.headers().firstValue("Location")).as("no redirect, so no loop").isEmpty();
        assertThat(first.body()).contains("<div id=\"root\">");
        assertThat(second.body()).as("the answer is stable, not a bounce").isEqualTo(first.body());
    }

    /**
     * The other half of the same rule (UC-WP-43, R-WP-22): the fallback may only rescue a route. A file
     * address that is missing stays an error — HTML where a module was expected is something a browser
     * cannot read, and a silent fallback would hide a broken build.
     */
    @Test
    // UC-WP-43
    void aMissingAssetStaysAnError() {
        for (String path : List.of("/ui/assets/index-does-not-exist.js", "/ui/no-such-file.map")) {
            ResponseEntity<byte[]> res = http.getForEntity(url(path), byte[].class);

            assertThat(res.getStatusCode())
                    .as("%s is shaped like a file, so it is missing and not a route", path)
                    .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(res.getHeaders().getContentType())
                    .as("%s is never answered with the app", path)
                    .isNotEqualTo(MediaType.TEXT_HTML);
            assertThat(new String(res.getBody() == null ? new byte[0] : res.getBody(), StandardCharsets.UTF_8))
                    .doesNotContain("<div id=\"root\">");
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
