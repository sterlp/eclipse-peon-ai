package org.sterl.open.aifactory.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import org.sterl.open.aifactory.AifactoryApplication;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * The HTTP contract of the registry. No git, no network — a fake {@link GitService} that records
 * <em>when</em> it ran, which is what makes "201 only after the clone" and "reused url never clones"
 * provable. Every test starts from an empty factory home.
 */
@SpringBootTest(classes = { AifactoryApplication.class, FakeGitConfig.class })
@Import(FakeGitConfig.class)
@AutoConfigureMockMvc
class ProjectControllerTest {

    private static final String URL = "https://git.example.com/acme/demo.git";
    private static final Path HOME = createHome();

    @Autowired
    MockMvc mvc;

    @Autowired
    FakeGitConfig fakeGit;

    /** Anything outside {@code <home>/projects} that a poisoned registry must not reach. */
    @TempDir
    Path victim;

    @DynamicPropertySource
    static void isolateHome(DynamicPropertyRegistry registry) {
        registry.add("aifactory.home", () -> HOME.toString());
    }

    @BeforeEach
    void emptyHome() {
        deleteTree(HOME);
        createDirectories(HOME);
        fakeGit.cloneTargets.clear();
        fakeGit.failWith = null;
    }

    @AfterAll
    static void removeHome() {
        deleteTree(HOME);
    }

    @Test
    void listIsEmptyOnFreshHome() throws Exception {
        mvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    // UC-WP-2
    void addProjectClonesBeforeListing() throws Exception {
        mvc.perform(post("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"gitUrl\":\"" + URL + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("demo"))
                .andExpect(jsonPath("$.state").value("READY"))
                .andExpect(jsonPath("$.localPath").value(endsWith("projects/demo")));

        // the 201 was only reached after the checkout existed — the clone ran exactly once, into home
        assertThat(fakeGit.cloneTargets).singleElement().isEqualTo(projectsDir().resolve("demo"));

        mvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("demo"));
    }

    @Test
    // UC-WP-3
    void sameUrlReusesExistingClone() throws Exception {
        String id = createdId(URL);

        // trailing slash is the same repository: 200, the existing project, and no second clone
        mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON)
                .content("{\"gitUrl\":\"https://git.example.com/acme/demo/\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("demo"));

        assertThat(fakeGit.cloneTargets).hasSize(1);
        mvc.perform(get("/api/projects")).andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(id));
    }

    @Test
    // UC-WP-4
    void failedCloneLeavesListUnchanged() throws Exception {
        Path target = projectsDir().resolve("gone");
        fakeGit.failWith = new CloneFailedException("https://git.example.com/acme/gone.git", target,
                "fatal: repository 'https://git.example.com/acme/gone.git' not found");

        mvc.perform(post("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"gitUrl\":\"https://git.example.com/acme/gone.git\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(containsString("fatal: repository")));

        mvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
        assertThat(projectFolders()).isEmpty(); // this proof does not depend on git being installed
    }

    @Test
    // UC-WP-26
    void sameNameDifferentUrlRejected() throws Exception {
        createdId("https://git.example.com/a/demo.git");

        mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON)
                .content("{\"gitUrl\":\"https://git.example.com/b/demo.git\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("demo")));

        assertThat(fakeGit.cloneTargets).hasSize(1);
        mvc.perform(get("/api/projects")).andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));
        assertThat(projectFolders()).singleElement().isEqualTo(projectsDir().resolve("demo"));
    }

    @Test
    // UC-WP-27
    void existingNonGitDirectoryIsNeverOverwritten() throws Exception {
        Path checkout = projectsDir().resolve("demo");
        createDirectories(checkout);
        Files.writeString(checkout.resolve("sentinel.txt"), "mine");
        fakeGit.failWith = new IllegalStateException(
                "target folder " + checkout + " exists but is not a git clone");

        mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON)
                .content("{\"gitUrl\":\"" + URL + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("demo")));

        assertThat(checkout.resolve("sentinel.txt")).exists();
        mvc.perform(get("/api/projects")).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    // UC-WP-28
    void removeDeletesEntryAndCheckout() throws Exception {
        String id = createdId(URL);
        Path checkout = projectsDir().resolve("demo");
        assertThat(checkout).isDirectory();

        mvc.perform(delete("/api/projects/{id}", id)).andExpect(status().isNoContent());

        assertThat(checkout).doesNotExist();
        mvc.perform(get("/api/projects")).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    // UC-WP-29
    void removeNeverDeletesOutsideProjectDir() throws Exception {
        Path outsideCheckout = victim.resolve("checkout");
        createDirectories(outsideCheckout);
        Files.writeString(outsideCheckout.resolve("victim.txt"), "do not delete me");

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

            mvc.perform(delete("/api/projects/{id}", "demo-1234567"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("demo-1234567")));

            // the poisoned row is refused on every call — never served, never silently dropped
            mvc.perform(get("/api/projects")).andExpect(status().isConflict());

            assertThat(outsideCheckout.resolve("victim.txt")).exists();
        }

        assertThat(fakeGit.cloneTargets).isEmpty();
    }

    @Test
    // UC-WP-30
    void credentialUrlRejectedAndNothingStored() throws Exception {
        for (String url : List.of("https://user:token@git.example.com/a/demo.git",
                "https://user@git.example.com/a/demo.git")) {
            MvcResult response = mvc.perform(post("/api/projects")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"gitUrl\":\"" + url + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            String body = response.getResponse().getContentAsString();
            assertThat(body).contains("credentials").doesNotContain("user").doesNotContain("token");
        }

        assertThat(fakeGit.cloneTargets).isEmpty();
        assertThat(HOME.resolve(FactoryHome.PROJECTS_FILE)).doesNotExist();
        mvc.perform(get("/api/projects")).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    // UC-WP-33
    void nonHttpsUrlRejectedNothingStored() throws Exception {
        for (String url : List.of("git@github.com:acme/demo.git",
                "ssh://git.example.com/acme/demo.git",
                "file:///tmp/demo")) {
            mvc.perform(post("/api/projects")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"gitUrl\":\"" + url + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("https")));
        }

        assertThat(fakeGit.cloneTargets).isEmpty();
        assertThat(HOME.resolve(FactoryHome.PROJECTS_FILE)).doesNotExist();
        mvc.perform(get("/api/projects")).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    // UC-WP-31
    void stalledCloneAbortsAndLeavesNothing() throws Exception {
        Path target = projectsDir().resolve("slow");
        fakeGit.failWith = new CloneFailedException("https://git.example.com/acme/slow.git", target,
                "git clone timed out after PT0.02S");

        mvc.perform(post("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"gitUrl\":\"https://git.example.com/acme/slow.git\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("timed out")));

        mvc.perform(get("/api/projects")).andExpect(jsonPath("$").isEmpty());
        assertThat(target).doesNotExist();
        assertThat(projectFolders()).isEmpty();
    }

    @Test
    void unknownIdReturns404() throws Exception {
        mvc.perform(delete("/api/projects/{id}", "nope-1234567"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("nope-1234567")));
    }

    /** Registers {@code gitUrl} and returns the id the API handed out. */
    private String createdId(String gitUrl) throws Exception {
        MvcResult created = mvc.perform(post("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"gitUrl\":\"" + gitUrl + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(created.getResponse().getContentAsString(), "$.id");
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

    private static Path createHome() {
        try {
            return Files.createTempDirectory("aifactory-api-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

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
