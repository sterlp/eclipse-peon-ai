package org.sterl.open.aifactory.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * Registry behaviour: entry only after a successful clone, identity over the normalized url, conflict
 * over the folder name, remove = entry + checkout and never anything outside the projects dir.
 */
class ProjectServiceTest {

    private static final String URL = "https://git.example.com/acme/demo.git";

    @TempDir
    Path homeDir;

    /** Stands in for anything outside the factory home that must survive a remove. */
    @TempDir
    Path outside;

    private final RecordingGit git = new RecordingGit();

    private ProjectService newService() {
        return new ProjectService(FactoryHome.of(homeDir), git);
    }

    @Test
    // UC-WP-15
    void entrySurvivesReload() throws IOException {
        Project added = newService().add(URL).project();

        Project reloaded = newService().projects().getFirst();
        assertThat(reloaded.id()).isEqualTo(added.id());
        assertThat(reloaded.gitUrl()).isEqualTo(URL);
        assertThat(newService().projects().getFirst().localPath())
                .isEqualTo(homeDir.resolve("projects").resolve("demo"));
        // the file keeps the checkout relative, so a moved home stays readable
        String stored = Files.readString(homeDir.resolve("projects.json"));
        assertThat(stored).contains("projects/demo").doesNotContain(homeDir.toString());
    }

    @Test
    // UC-WP-3
    void sameUrlReusesExistingClone() {
        Project first = newService().add(URL).project();

        AddProjectResult again = newService().add("https://git.example.com/acme/demo/");

        assertThat(again.created()).isFalse();
        assertThat(again.project().id()).isEqualTo(first.id());
        assertThat(git.cloneTargets).hasSize(1);
        assertThat(newService().projects()).singleElement()
                .extracting(Project::id).isEqualTo(first.id());
    }

    @Test
    // UC-WP-26
    void sameNameDifferentUrlRejected() throws IOException {
        newService().add("https://git.example.com/a/demo.git");

        assertThatThrownBy(() -> newService().add("https://git.example.com/b/demo.git"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("demo");

        assertThat(git.cloneTargets).hasSize(1);
        assertThat(newService().projects()).hasSize(1);
        assertThat(projectFolders()).containsExactly(homeDir.resolve("projects").resolve("demo"));
    }

    @Test
    void blankOrUnparsableUrlIsRejectedWithoutAnyClone() {
        assertThatThrownBy(() -> newService().add("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be empty");
        assertThatThrownBy(() -> newService().add("https://.git"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a git url");

        assertThat(git.cloneTargets).isEmpty();
        assertThat(homeDir.resolve("projects.json")).doesNotExist();
    }

    @Test
    // UC-WP-30
    void credentialUrlRejectedAndNothingStored() {
        List<String> credentialUrls = List.of(
                "https://user:token@git.example.com/a/demo.git",
                "https://user@git.example.com/a/demo.git");

        for (String url : credentialUrls) {
            assertThatThrownBy(() -> newService().add(url))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("credentials")
                    .hasMessageNotContaining("user")
                    .hasMessageNotContaining("token");
        }

        assertThat(git.cloneTargets).isEmpty();
        assertThat(homeDir.resolve("projects.json")).doesNotExist();
        assertThat(homeDir.resolve("projects")).doesNotExist();
    }

    @Test
    // UC-WP-33
    void nonHttpsUrlRejectedNothingStored() {
        for (String url : List.of("git@github.com:acme/demo.git",
                "ssh://git.example.com/acme/demo.git",
                "file:///tmp/demo",
                "http://git.example.com/acme/demo.git")) {
            assertThatThrownBy(() -> newService().add(url))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("https");
        }

        assertThat(git.cloneTargets).isEmpty();
        assertThat(homeDir.resolve("projects.json")).doesNotExist();
        assertThat(homeDir.resolve("projects")).doesNotExist();
    }

    @Test
    // UC-WP-28
    void removeDeletesEntryAndCheckout() {
        ProjectService service = newService();
        Project added = service.add(URL).project();
        Path checkout = homeDir.resolve("projects").resolve("demo");

        service.remove(added.id());

        assertThat(service.projects()).isEmpty();
        assertThat(checkout).doesNotExist();
    }

    @Test
    // UC-WP-29
    void removeNeverDeletesOutsideProjectDir() throws IOException {
        Path victim = outside.resolve("checkout");
        Files.createDirectories(victim);
        Files.writeString(victim.resolve("victim.txt"), "do not delete me");

        assertPoisonedEntryIsRefusedAndNothingIsDeleted(homeDir.relativize(victim).toString());

        // the prefix trap: <home>twins shares a string prefix with <home>, but is not below it
        Path twin = Files.createDirectories(homeDir.resolveSibling(homeDir.getFileName() + "twins"));
        Path twinCheckout = twin.resolve("projects").resolve("demo");
        Files.createDirectories(twinCheckout);
        Files.writeString(twinCheckout.resolve("victim.txt"), "do not delete me");
        assertPoisonedEntryIsRefusedAndNothingIsDeleted(
                homeDir.relativize(twinCheckout).toString());

        // an absolute path is never obeyed, no matter how plausible it looks
        assertPoisonedEntryIsRefusedAndNothingIsDeleted(victim.toAbsolutePath().toString());

        assertThat(victim.resolve("victim.txt")).exists();
        assertThat(twinCheckout.resolve("victim.txt")).exists();
    }

    @Test
    void deleteTreeRefusesTheProjectsDirItselfAndItsNeighbours() {
        Path projectsDir = homeDir.resolve("projects");

        assertThatThrownBy(() -> ProjectPaths.requireInsideProjectsDir(projectsDir, projectsDir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("checkouts live below");
        assertThatThrownBy(() -> ProjectPaths.requireInsideProjectsDir(projectsDir,
                homeDir.resolve("state").resolve("demo")))
                        .isInstanceOf(IllegalStateException.class);

        assertThat(ProjectPaths.requireInsideProjectsDir(projectsDir, projectsDir.resolve("demo")))
                .isEqualTo(projectsDir.resolve("demo").toAbsolutePath().normalize());
    }

    @Test
    void unknownIdRemovalThrows() {
        ProjectService service = newService();

        assertThatThrownBy(() -> service.remove("nope-1234567"))
                .isInstanceOf(ProjectNotFoundException.class)
                .hasMessageContaining("unknown project id");

        assertThat(service.projects()).isEmpty();
    }

    @Test
    void failedCloneIsNotRegistered() {
        git.failWith = new CloneFailedException(URL, homeDir.resolve("projects/demo"),
                "fatal: repository not found");

        assertThatThrownBy(() -> newService().add(URL))
                .isInstanceOf(CloneFailedException.class)
                .hasMessageContaining("repository not found");

        assertThat(newService().projects()).isEmpty();
    }

    @Test
    void unsafeProjectNameIsRejectedBeforeAnyClone() {
        assertThatThrownBy(() -> newService().add("https://git.example.com/acme/..%2fdanger"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported project name");

        assertThat(git.cloneTargets).isEmpty();
    }

    /** Writes the registry by hand, removes the entry and demands that nothing left the projects dir. */
    private void assertPoisonedEntryIsRefusedAndNothingIsDeleted(String storedLocalPath)
            throws IOException {
        Path registry = homeDir.resolve("projects.json");
        Files.writeString(registry, """
                [ {
                  "id" : "demo-1234567",
                  "name" : "demo",
                  "gitUrl" : "https://git.example.com/acme/demo.git",
                  "localPath" : "%s",
                  "state" : "READY"
                } ]""".formatted(storedLocalPath));
        String before = Files.readString(registry);

        assertThatThrownBy(() -> newService().remove("demo-1234567"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> newService().projects()).isInstanceOf(IllegalStateException.class);

        assertThat(Files.readString(registry)).isEqualTo(before);
    }

    private List<Path> projectFolders() throws IOException {
        Path projectsDir = homeDir.resolve("projects");
        if (!Files.isDirectory(projectsDir)) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(projectsDir)) {
            return children.filter(Files::isDirectory).toList();
        }
    }

    /**
     * Stands in for git: records the targets, can fail — and on failure leaves a <em>partial</em>
     * checkout behind, exactly like a killed real clone, so the cleanup is really exercised.
     */
    private static final class RecordingGit implements GitService {

        final List<Path> cloneTargets = new ArrayList<>();
        CloneFailedException failWith;

        @Override
        public boolean clone(String gitUrl, Path target) {
            cloneTargets.add(target);
            try {
                Files.createDirectories(target);
                Files.writeString(target.resolve("partial.txt"), "half a checkout");
                if (failWith != null) {
                    throw failWith;
                }
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
            return true;
        }
    }
}
