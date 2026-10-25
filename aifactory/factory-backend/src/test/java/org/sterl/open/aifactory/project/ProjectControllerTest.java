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
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.sterl.open.aifactory.AifactoryApplication;

/**
 * The HTTP contract of the registry. No git, no network — a fake {@link GitService} that records
 * <em>when</em> it ran, which is what makes "201 only after the clone" provable. Every test starts
 * from an empty factory home.
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

    @DynamicPropertySource
    static void isolateHome(DynamicPropertyRegistry registry) {
        registry.add("aifactory.home", () -> HOME.toString());
    }

    @BeforeEach
    void emptyHome() {
        deleteTree(HOME);
        createDirectories(HOME);
        fakeGit.cloneTargets.clear();
        fakeGit.failNext = false;
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
    void addProjectReturns201OnlyAfterClone() throws Exception {
        mvc.perform(post("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"gitUrl\":\"" + URL + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("demo"))
                .andExpect(jsonPath("$.state").value("READY"))
                .andExpect(jsonPath("$.localPath").value(endsWith("projects/demo")));

        // the 201 was only reached after the checkout existed — the clone ran exactly once, into home
        assertThat(fakeGit.cloneTargets).singleElement()
                .isEqualTo(HOME.resolve("projects").resolve("demo"));

        mvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("demo"));
    }

    @Test
    // UC-WP-4
    void failedCloneReturns400WithGitMessageAndListUnchanged() throws Exception {
        fakeGit.failNext = true;

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
    }

    @Test
    // UC-WP-3
    void duplicateUrlReturns400NamingThePresentProject() throws Exception {
        mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON)
                .content("{\"gitUrl\":\"" + URL + "\"}")).andExpect(status().isCreated());

        mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON)
                .content("{\"gitUrl\":\"https://git.example.com/acme/demo/\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("demo")));

        assertThat(fakeGit.cloneTargets).hasSize(1);
    }

    @Test
    void removeDeletesProjectAndCheckout() throws Exception {
        MvcResult created = mvc.perform(post("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"gitUrl\":\"" + URL + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.id");
        Path checkout = HOME.resolve("projects").resolve("demo");
        assertThat(checkout).isDirectory();

        mvc.perform(delete("/api/projects/{id}", id)).andExpect(status().isNoContent());

        assertThat(checkout).doesNotExist();
        mvc.perform(get("/api/projects")).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void unknownIdReturns404() throws Exception {
        mvc.perform(delete("/api/projects/{id}", "nope-1234567"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("nope-1234567")));
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
