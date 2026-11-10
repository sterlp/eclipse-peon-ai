package org.sterl.open.aifactory.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.jayway.jsonpath.JsonPath;

import org.sterl.open.aifactory.FactorySpringTest;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * The HTTP contract of the registry. No git, no network — a fake {@link GitService} that records
 * <em>when</em> it ran, which is what makes "201 only after the clone" and "reused url never clones"
 * provable. Every test starts from an empty factory home.
 *
 * <p>Real HTTP and not a mocked servlet layer: the shared context of {@link FactorySpringTest} runs a
 * server for the whole build anyway ({@code aifactory/AGENTS.md} — one context per build), so a MOCK
 * context of its own would buy a second boot for answers this one gives identically — the status, the
 * {@code ApiError} body and the serialization of {@code localPath} all come from the same handlers and
 * the same message converters.</p>
 */
class ProjectControllerTest extends FactorySpringTest {

    private static final String URL = "https://git.example.com/acme/demo.git";
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    /** Anything outside {@code <home>/projects} that a poisoned registry must not reach. */
    @TempDir
    Path victim;

    @Test
    void listIsEmptyOnFreshHome() throws Exception {
        HttpResponse<String> list = get("/api/projects");

        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(rows(list)).isEmpty();
    }

    @Test
    // UC-WP-2
    void addProjectClonesBeforeListing() throws Exception {
        HttpResponse<String> created = post("/api/projects", "{\"gitUrl\":\"" + URL + "\"}");

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(text(created, "$.name")).isEqualTo("demo");
        assertThat(text(created, "$.state")).isEqualTo("READY");
        String localPath = text(created, "$.localPath");
        assertThat(localPath).endsWith("projects/demo");

        // the 201 was only reached after the checkout existed — the clone ran exactly once, into home
        assertThat(fakeGit.cloneTargets).singleElement().isEqualTo(projectsDir().resolve("demo"));

        assertThat(text(get("/api/projects"), "$[0].name")).isEqualTo("demo");
    }

    @Test
    // UC-WP-3
    void sameUrlReusesExistingClone() throws Exception {
        String id = createdId(URL);

        // trailing slash is the same repository: 200, the existing project, and no second clone
        HttpResponse<String> reused = post("/api/projects",
                "{\"gitUrl\":\"https://git.example.com/acme/demo/\"}");

        assertThat(reused.statusCode()).isEqualTo(200);
        assertThat(text(reused, "$.id")).isEqualTo(id);
        assertThat(text(reused, "$.name")).isEqualTo("demo");

        assertThat(fakeGit.cloneTargets).hasSize(1);
        HttpResponse<String> list = get("/api/projects");
        assertThat(rows(list)).isNotEmpty();
        assertThat(text(list, "$[0].id")).isEqualTo(id);
    }

    @Test
    // UC-WP-4
    void failedCloneLeavesListUnchanged() throws Exception {
        Path target = projectsDir().resolve("gone");
        fakeGit.failWith = new CloneFailedException("https://git.example.com/acme/gone.git", target,
                "fatal: repository 'https://git.example.com/acme/gone.git' not found");

        HttpResponse<String> refused = post("/api/projects",
                "{\"gitUrl\":\"https://git.example.com/acme/gone.git\"}");

        assertThat(refused.statusCode()).isEqualTo(400);
        assertThat(number(refused, "$.status")).isEqualTo(400);
        assertThat(text(refused, "$.error")).isEqualTo("Bad Request");
        assertThat(text(refused, "$.message")).contains("fatal: repository");

        HttpResponse<String> list = get("/api/projects");
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(rows(list)).isEmpty();
        assertThat(projectFolders()).isEmpty(); // this proof does not depend on git being installed
    }

    @Test
    // UC-WP-26
    void sameNameDifferentUrlRejected() throws Exception {
        createdId("https://git.example.com/a/demo.git");

        HttpResponse<String> refused = post("/api/projects",
                "{\"gitUrl\":\"https://git.example.com/b/demo.git\"}");

        assertThat(refused.statusCode()).isEqualTo(400);
        assertThat(text(refused, "$.message")).contains("demo");

        assertThat(fakeGit.cloneTargets).hasSize(1);
        assertThat(rows(get("/api/projects"))).hasSize(1);
        assertThat(projectFolders()).singleElement().isEqualTo(projectsDir().resolve("demo"));
    }

    @Test
    // UC-WP-27
    void existingNonGitDirectoryIsNeverOverwritten() throws Exception {
        Path checkout = projectsDir().resolve("demo");
        Files.createDirectories(checkout);
        Files.writeString(checkout.resolve("sentinel.txt"), "mine");
        fakeGit.failWith = new IllegalStateException(
                "target folder " + checkout + " exists but is not a git clone");

        HttpResponse<String> refused = post("/api/projects", "{\"gitUrl\":\"" + URL + "\"}");

        assertThat(refused.statusCode()).isEqualTo(409);
        assertThat(text(refused, "$.message")).contains("demo");

        assertThat(checkout.resolve("sentinel.txt")).exists();
        assertThat(rows(get("/api/projects"))).isEmpty();
    }

    @Test
    // UC-WP-28
    void removeDeletesEntryAndCheckout() throws Exception {
        String id = createdId(URL);
        Path checkout = projectsDir().resolve("demo");
        assertThat(checkout).isDirectory();

        assertThat(delete("/api/projects/" + id).statusCode()).isEqualTo(204);

        assertThat(checkout).doesNotExist();
        assertThat(rows(get("/api/projects"))).isEmpty();
    }

    @Test
    // UC-WP-29
    void removeNeverDeletesOutsideProjectDir() throws Exception {
        Path outsideCheckout = victim.resolve("checkout");
        Files.createDirectories(outsideCheckout);
        Files.writeString(outsideCheckout.resolve("victim.txt"), "do not delete me");

        try {
            // both shapes of a hand-edited path: the absolute one and the relative escape
            for (String stored : List.of(outsideCheckout.toAbsolutePath().toString(),
                    HOME.relativize(outsideCheckout).toString())) {
                Files.writeString(HOME.resolve(FactoryHome.PROJECTS_FILE), """
                        [ {
                          "id" : "demo-1234567",
                          "name" : "demo",
                          "gitUrl" : "https://git.example.com/acme/demo.git",
                          "localPath" : "%s",
                          "state" : "READY"
                        } ]""".formatted(stored));

                HttpResponse<String> refused = delete("/api/projects/demo-1234567");
                assertThat(refused.statusCode()).isEqualTo(409);
                assertThat(text(refused, "$.message")).contains("demo-1234567");

                // the poisoned row is refused on every call — never served, never silently dropped
                assertThat(get("/api/projects").statusCode()).isEqualTo(409);

                assertThat(outsideCheckout.resolve("victim.txt")).exists();
            }

            assertThat(fakeGit.cloneTargets).isEmpty();
        } finally {
            // the row above is written by hand and production never writes one like it, but reading it
            // back is an IllegalStateException — on the shared home that would break the reset of every
            // following class. This test made the file, this test removes it.
            Files.deleteIfExists(HOME.resolve(FactoryHome.PROJECTS_FILE));
        }
    }

    @Test
    // UC-WP-30
    void credentialUrlRejectedAndNothingStored() throws Exception {
        for (String url : List.of("https://user:token@git.example.com/a/demo.git",
                "https://user@git.example.com/a/demo.git")) {
            HttpResponse<String> refused = post("/api/projects", "{\"gitUrl\":\"" + url + "\"}");

            assertThat(refused.statusCode()).isEqualTo(400);
            assertThat(refused.body()).contains("credentials").doesNotContain("user").doesNotContain("token");
            assertThat(fields(refused)).containsExactly("gitUrl");
        }

        assertThat(fakeGit.cloneTargets).isEmpty();
        assertThat(HOME.resolve(FactoryHome.PROJECTS_FILE)).doesNotExist();
        assertThat(rows(get("/api/projects"))).isEmpty();
    }

    @Test
    // UC-WP-33
    void nonHttpsUrlRejectedNothingStored() throws Exception {
        for (String url : List.of("git@github.com:acme/demo.git",
                "ssh://git.example.com/acme/demo.git",
                "file:///tmp/demo")) {
            HttpResponse<String> refused = post("/api/projects", "{\"gitUrl\":\"" + url + "\"}");

            assertThat(refused.statusCode()).as("url %s", url).isEqualTo(400);
            assertThat(text(refused, "$.message")).contains("https");
            assertThat(fields(refused)).containsExactly("gitUrl");
        }

        assertThat(fakeGit.cloneTargets).isEmpty();
        assertThat(HOME.resolve(FactoryHome.PROJECTS_FILE)).doesNotExist();
        assertThat(rows(get("/api/projects"))).isEmpty();
    }

    @Test
    // UC-WP-92
    void anEmptyUrlNamesItsFieldButAMissingBodyNamesNothing() throws Exception {
        HttpResponse<String> refused = post("/api/projects", "{\"gitUrl\":\"   \"}");

        assertThat(refused.statusCode()).isEqualTo(400);
        assertThat(text(refused, "$.message")).contains("gitUrl");
        assertThat(fields(refused)).containsExactly("gitUrl");

        // a body that is absent altogether names no field — the registry still refuses it, as its own line
        HttpResponse<String> missing = post("/api/projects", "");
        assertThat(missing.statusCode()).isEqualTo(400);
        assertThat(fields(missing)).isEmpty();

        assertThat(fakeGit.cloneTargets).isEmpty();
        assertThat(HOME.resolve(FactoryHome.PROJECTS_FILE)).doesNotExist();
    }

    @Test
    // UC-WP-31
    void stalledCloneAbortsAndLeavesNothing() throws Exception {
        Path target = projectsDir().resolve("slow");
        fakeGit.failWith = new CloneFailedException("https://git.example.com/acme/slow.git", target,
                "git clone timed out after PT0.02S");

        HttpResponse<String> refused = post("/api/projects",
                "{\"gitUrl\":\"https://git.example.com/acme/slow.git\"}");

        assertThat(refused.statusCode()).isEqualTo(400);
        assertThat(text(refused, "$.message")).contains("timed out");

        assertThat(rows(get("/api/projects"))).isEmpty();
        assertThat(target).doesNotExist();
        assertThat(projectFolders()).isEmpty();
    }

    @Test
    void unknownIdReturns404() throws Exception {
        HttpResponse<String> missing = delete("/api/projects/nope-1234567");

        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(number(missing, "$.status")).isEqualTo(404);
        assertThat(text(missing, "$.message")).contains("nope-1234567");
    }

    // ------------------------------------------------------------------ the wire

    /** Registers {@code gitUrl} and returns the id the API handed out. */
    private String createdId(String gitUrl) throws Exception {
        HttpResponse<String> created = post("/api/projects", "{\"gitUrl\":\"" + gitUrl + "\"}");
        assertThat(created.statusCode()).isEqualTo(201);
        return text(created, "$.id");
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(uri(path)));
    }

    private HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)));
    }

    private HttpResponse<String> delete(String path) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(uri(path)).method("DELETE", HttpRequest.BodyPublishers.noBody()));
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    /**
     * The JDK client, not RestTemplate: {@code HttpURLConnection} refuses PATCH, so this is the client
     * the methods of the same resource are asked with ({@code ProjectFilesTest}).
     */
    private static HttpResponse<String> send(HttpRequest.Builder request)
            throws IOException, InterruptedException {
        return CLIENT.send(request.timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * A field of the answer as text. Typed on purpose — a generic {@code <T>} that lands straight in
     * {@code assertThat} is ambiguous for javac, and a missing path is a
     * {@code PathNotFoundException}, never a quiet {@code null}.
     */
    private static String text(HttpResponse<String> answer, String jsonPath) {
        Object value = JsonPath.read(answer.body(), jsonPath);
        return String.valueOf(value);
    }

    /** A numeric field of the answer — {@code $.status} of the {@code ApiError} body. */
    private static int number(HttpResponse<String> answer, String jsonPath) {
        return ((Number) JsonPath.read(answer.body(), jsonPath)).intValue();
    }

    /**
     * The fields the answer names as wrong (UC-WP-92). A refusal without field errors answers
     * {@code "fieldErrors":null} — null and an empty list both mean: no field is named.
     */
    private static List<String> fields(HttpResponse<String> answer) {
        Object named = JsonPath.read(answer.body(), "$.fieldErrors");
        return named == null ? List.of() : JsonPath.read(answer.body(), "$.fieldErrors[*].field");
    }

    /** The list body as rows: the {@code [} is the proof that the answer is an array, not an object. */
    private static List<?> rows(HttpResponse<String> answer) {
        assertThat(answer.body()).startsWith("[");
        return JsonPath.read(answer.body(), "$");
    }

    private static Path projectsDir() {
        return HOME.resolve(FactoryHome.PROJECTS_DIR);
    }

    private static List<Path> projectFolders() {
        if (!Files.isDirectory(projectsDir())) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(projectsDir())) {
            return children.toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
