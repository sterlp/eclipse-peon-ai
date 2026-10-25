package org.sterl.open.aifactory.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * Registry behaviour: entry only after a successful clone, one entry per normalized url, remove
 * = entry + checkout.
 */
class ProjectServiceTest {

    private static final String URL = "https://git.example.com/acme/demo.git";

    @TempDir
    Path homeDir;

    private final RecordingGit git = new RecordingGit();

    private ProjectService newService() {
        return new ProjectService(FactoryHome.of(homeDir), git);
    }

    @Test
    // UC-WP-15
    void entrySurvivesReload() throws IOException {
        Project added = newService().add(URL);

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
    void sameNormalizedUrlReusesExistingDescriptor() {
        Project first = newService().add(URL);

        assertThatThrownBy(() -> newService().add("https://git.example.com/acme/demo/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("demo")
                .hasMessageContaining("already present");

        assertThat(git.cloneTargets).hasSize(1);
        assertThat(newService().projects()).singleElement()
                .extracting(Project::id).isEqualTo(first.id());
    }

    @Test
    void removeDeletesEntryAndCloneFolder() {
        ProjectService service = newService();
        Project added = service.add(URL);
        Path checkout = homeDir.resolve("projects").resolve("demo");

        service.remove(added.id());

        assertThat(service.projects()).isEmpty();
        assertThat(checkout).doesNotExist();
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
