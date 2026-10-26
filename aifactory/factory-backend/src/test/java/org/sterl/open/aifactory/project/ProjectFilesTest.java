package org.sterl.open.aifactory.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.NoOpResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.sterl.open.aifactory.AifactoryApplication;
import org.sterl.open.aifactory.shared.FactoryHome;
import org.sterl.open.aifactory.shared.TmpRoots;

/**
 * The read edge of the file area over real HTTP: two roots, names only, and every way out of a root
 * answered with 400. Real HTTP because the 405 and the 400 are framework answers, not ours — a mocked
 * layer would assert our own opinion about them.
 *
 * <p>The escape target is a {@link TempDir} of its own and <em>never</em> the system tmp: tmp is one
 * of the two roots, so a path into it proves nothing. The factory home is {@code target/inc4b-home}
 * rather than a temp dir, so {@code ..} really leaves the checkout ({@code aifactory/AGENTS-DEV.md}).</p>
 */
@SpringBootTest(classes = AifactoryApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FakeGitConfig.class)
class ProjectFilesTest {

    private static final String URL = "https://git.example.com/acme/demo.git";
    private static final Path HOME = Path.of("target", "inc4b-home").toAbsolutePath();
    private static final ObjectMapper MAPPING = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Autowired
    FakeGitConfig fakeGit;

    /** Outside both roots — what an escaping path must never reach. */
    @TempDir
    Path outside;

    private final RestTemplate http = new RestTemplate();

    private String projectId;
    private Path checkout;

    @DynamicPropertySource
    static void isolateHome(DynamicPropertyRegistry registry) {
        registry.add("aifactory.home", () -> HOME.toString());
    }

    @BeforeEach
    void registerProjectAndFillCheckout() throws Exception {
        http.setErrorHandler(new NoOpResponseErrorHandler());
        deleteTree(HOME);
        createDirectories(HOME);
        fakeGit.cloneTargets.clear();
        fakeGit.failWith = null;

        ResponseEntity<String> created = http.postForEntity(base() + "/api/projects",
                json("{\"gitUrl\":\"" + URL + "\"}"), String.class);
        projectId = MAPPING.readTree(created.getBody()).get("id").asText();

        checkout = HOME.resolve(FactoryHome.PROJECTS_DIR).resolve("demo");
        Files.createDirectories(checkout.resolve("src").resolve("main"));
        Files.writeString(checkout.resolve("README.md"), "# demo\n");
        Files.writeString(checkout.resolve("src").resolve("main").resolve("App.java"), "class App {}\n");
    }

    @AfterAll
    static void removeHome() {
        deleteTree(HOME);
    }

    @Test
    // UC-WP-19
    void fileAreaReadsCheckoutAndSystemTmp() throws Exception {
        JsonNode rootListing = get(files().queryParam("root", "checkout").build());
        assertThat(rootListing.get("kind").asText()).isEqualTo("directory");
        assertThat(rootListing.get("root").asText()).isEqualTo("checkout");
        assertThat(rootListing.get("path").asText()).isEmpty();
        assertThat(named(rootListing, "src").get("directory").asBoolean()).isTrue();
        assertThat(named(rootListing, "README.md").get("directory").asBoolean()).isFalse();
        assertThat(named(rootListing, ".git")).isNotNull(); // the listing is the directory, not a filtered view

        JsonNode nested = get(files().queryParam("root", "checkout")
                .queryParam("path", "src/main").build());
        assertThat(nested.get("kind").asText()).isEqualTo("directory");
        assertThat(nested.get("path").asText()).isEqualTo("src/main");
        assertThat(named(nested, "App.java")).isNotNull();

        JsonNode file = get(files().queryParam("root", "checkout")
                .queryParam("path", "README.md").build());
        assertThat(file.get("kind").asText()).isEqualTo("file");
        assertThat(file.get("text").asText()).isEqualTo("# demo\n");
        assertThat(file.get("binary").asBoolean()).isFalse();
        assertThat(file.get("truncated").asBoolean()).isFalse();

        String marker = "inc4b-tmp-" + UUID.randomUUID() + ".txt";
        Path markerFile = TmpRoots.systemTmp().resolve(marker);
        try {
            Files.writeString(markerFile, "written into the system tmp");
            JsonNode fromTmp = get(files().queryParam("root", "tmp").queryParam("path", marker).build());
            assertThat(fromTmp.get("kind").asText()).isEqualTo("file");
            assertThat(fromTmp.get("root").asText()).isEqualTo("tmp");
            assertThat(fromTmp.get("text").asText()).isEqualTo("written into the system tmp");

            // the two roots stay two: the same name below the checkout is simply not there
            assertThat(getStatus(files().queryParam("root", "checkout").queryParam("path", marker).build()))
                    .isEqualTo(404);
        } finally {
            Files.deleteIfExists(markerFile);
        }
    }

    @Test
    // UC-WP-42
    void fileAreaNeverEscapesItsRoot() throws Exception {
        Path secret = outside.resolve("secret.txt");
        Files.writeString(secret, "not for the file area");

        for (String escape : new String[] { "../secret.txt", "src/../../secret.txt",
                "src/main/../../../secret.txt" }) {
            ResponseEntity<String> refused = getResponse(files().queryParam("root", "checkout")
                    .queryParam("path", escape).build());
            assertThat(refused.getStatusCode().value())
                    .as("traversal %s", escape)
                    .isEqualTo(400);
            assertThat(refused.getBody()).contains("checkout").doesNotContain(secret.toString());
        }

        // an absolute path is refused as what it is, without resolving it at all
        ResponseEntity<String> absolute = getResponse(files().queryParam("root", "checkout")
                .queryParam("path", secret.toAbsolutePath().toString()).build());
        assertThat(absolute.getStatusCode().value()).isEqualTo(400);
        assertThat(absolute.getBody()).contains("checkout").doesNotContain(secret.toString());

        // a link inside the checkout leaving it is a 400, and the refusal still names no target
        Files.createSymbolicLink(checkout.resolve("escape.txt"), secret);
        ResponseEntity<String> escapingLink = getResponse(files().queryParam("root", "checkout")
                .queryParam("path", "escape.txt").build());
        assertThat(escapingLink.getStatusCode().value()).isEqualTo(400);
        assertThat(escapingLink.getBody()).contains("checkout").doesNotContain(secret.toString());
        assertThat(secret).exists(); // refused, not consumed

        // the guard is not a false-alarm machine: a link that stays inside reads
        Files.createSymbolicLink(checkout.resolve("inside-link.md"), checkout.resolve("README.md"));
        JsonNode linked = get(files().queryParam("root", "checkout").queryParam("path", "inside-link.md").build());
        assertThat(linked.get("kind").asText()).isEqualTo("file");
        assertThat(linked.get("text").asText()).isEqualTo("# demo\n");

        // an unknown root is not the checkout by default either
        assertThat(getStatus(files().queryParam("root", "etwasanderes").build())).isEqualTo(400);
    }

    @Test
    void fileAreaIsReadOnlyAndBounded() throws Exception {
        for (String write : List.of("PUT", "POST", "PATCH", "DELETE")) {
            assertThat(statusOf(write, files().queryParam("root", "checkout").build().toUri()))
                    .as("%s on the read route", write)
                    .isEqualTo(405);
        }

        Path many = checkout.resolve("many");
        Files.createDirectories(many);
        for (int index = 0; index < 600; index++) {
            Files.writeString(many.resolve("entry-" + index + ".txt"), "x");
        }
        JsonNode listing = get(files().queryParam("root", "checkout").queryParam("path", "many").build());
        assertThat(listing.get("entries")).hasSize(ProjectFilesController.MAX_ENTRIES);
        assertThat(listing.get("truncated").asBoolean()).isTrue();
        assertThat(listing.get("entries").get(0).get("name").asText()).startsWith("entry-");

        Files.writeString(checkout.resolve("big.txt"), "a".repeat(70000));
        JsonNode big = get(files().queryParam("root", "checkout").queryParam("path", "big.txt").build());
        assertThat(big.get("truncated").asBoolean()).isTrue();
        assertThat(big.get("text").asText()).hasSizeLessThanOrEqualTo(ProjectFilesController.MAX_TEXT_BYTES);
        assertThat(big.get("size").asLong()).isEqualTo(70000L);
        assertThat(big.get("binary").asBoolean()).isFalse();

        Files.write(checkout.resolve("binary.bin"), new byte[] { 1, 2, 0, 4, 5 });
        JsonNode binary = get(files().queryParam("root", "checkout").queryParam("path", "binary.bin").build());
        assertThat(binary.get("binary").asBoolean()).isTrue();
        assertThat(binary.get("text").isNull()).isTrue();
        assertThat(binary.get("size").asLong()).isEqualTo(5L);

        assertThat(getStatus(files("nope-1234567").build())).isEqualTo(404);
        assertThat(getStatus(files().queryParam("root", "checkout")
                .queryParam("path", "not-here.txt").build())).isEqualTo(404);

        // a successful answer carries names only — the absolute path of the checkout never appears
        String body = getResponse(files().queryParam("root", "checkout").build()).getBody();
        assertThat(body).doesNotContain(HOME.toString()).doesNotContain(checkout.toString())
                .doesNotContain(TmpRoots.systemTmp().toString());
        for (JsonNode entry : get(files().queryParam("root", "checkout").build()).get("entries")) {
            assertThat(entry.get("name").asText()).doesNotStartWith("/");
        }
    }

    // ------------------------------------------------------------------ the wire

    private String base() {
        return "http://localhost:" + port;
    }

    /** The files route of the registered project; {@code root} and {@code path} are the caller's. */
    private UriComponentsBuilder files() {
        return files(projectId);
    }

    private UriComponentsBuilder files(String id) {
        return UriComponentsBuilder.fromUriString(base() + "/api/projects/" + id + "/files");
    }

    private static HttpEntity<String> json(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private JsonNode get(UriComponents uri) throws Exception {
        return MAPPING.readTree(getResponse(uri).getBody());
    }

    private ResponseEntity<String> getResponse(UriComponents uri) {
        return http.getForEntity(uri.toUri(), String.class);
    }

    private int getStatus(UriComponents uri) {
        return http.getForEntity(uri.toUri(), String.class).getStatusCode().value();
    }

    /**
     * The JDK client, not RestTemplate: {@code HttpURLConnection} refuses PATCH, so the shared client
     * could never ask for the method whose 405 is the proof.
     */
    private static int statusOf(String method, URI uri) throws Exception {
        HttpResponse<String> answer = CLIENT.send(
                HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                        .method(method, HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        return answer.statusCode();
    }

    private static JsonNode named(JsonNode listing, String name) {
        for (JsonNode entry : listing.get("entries")) {
            if (name.equals(entry.get("name").asText())) {
                return entry;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ home

    private static void createDirectories(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteTree(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> tree = Files.walk(dir)) {
            tree.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort around the test run
                }
            });
        } catch (IOException ignored) {
            // best effort around the test run
        }
    }
}
