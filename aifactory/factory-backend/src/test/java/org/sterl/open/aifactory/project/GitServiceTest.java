package org.sterl.open.aifactory.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import org.sterl.open.aifactory.project.CommandRunner.CommandResult;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * Real git through the {@link CommandRunner} seam — local {@code file://} origins only, plus the two
 * branches a real clone cannot show on demand (timeout, non-git target).
 *
 * <p>URL policy is not tested here: {@code file://} is the only offline git that exists, and the
 * https-only rule is {@link ProjectService}'s job (R-WP-20). This class proves the connector's own
 * rules — reuse, never overwrite, never leave a partial checkout, never delete outside the
 * projects dir.</p>
 */
@EnabledIf("gitAvailable")
class GitServiceTest {

    @TempDir
    Path work;

    private final CommandRunner commands = new ProcessCommandRunner();
    private GitService git;
    private Path origin;

    static boolean gitAvailable() {
        return new ProcessCommandRunner().run(null, List.of("git", "--version"), Duration.ofSeconds(10)).ok();
    }

    @BeforeEach
    void cloneOriginWithOneCommit() {
        git = newGitService(commands);
        origin = work.resolve("origin");
        run(origin, "git", "init", "-q", ".");
        run(origin, "git", "-c", "user.email=f@actory.test", "-c", "user.name=Factory",
                "commit", "-q", "--allow-empty", "-m", "first");
    }

    @Test
    // UC-WP-2
    void clonesLocalRepoIntoTarget() {
        Path target = projectsDir().resolve("demo");

        boolean cloned = git.clone(origin.toUri().toString(), target);

        assertThat(cloned).isTrue();
        assertThat(target.resolve(".git")).isDirectory();
    }

    @Test
    // UC-WP-3
    void existingCloneIsReusedAndNotTouched() throws IOException {
        Path target = projectsDir().resolve("demo");
        git.clone(origin.toUri().toString(), target);
        Files.writeString(target.resolve("sentinel.txt"), "mine");

        boolean cloned = git.clone(origin.toUri().toString(), target);

        assertThat(cloned).isFalse();
        assertThat(target.resolve("sentinel.txt")).exists();
    }

    @Test
    // UC-WP-4
    void invalidUrlFailsFastAndLeavesNoPartialClone() {
        Path target = projectsDir().resolve("nope");

        assertThatThrownBy(() -> git.clone("https://127.0.0.1:1/nope.git", target))
                .isInstanceOf(CloneFailedException.class)
                .satisfies(e -> assertThat(((CloneFailedException) e).gitOutput()).isNotBlank());

        assertThat(target).doesNotExist();
    }

    @Test
    // UC-WP-27
    void existingNonGitDirectoryIsNeverOverwritten() throws IOException {
        List<List<String>> commandsRun = new ArrayList<>();
        GitService recording = newGitService((wd, command, timeout) -> {
            commandsRun.add(command);
            return new CommandResult(0, "", false);
        });
        Path target = projectsDir().resolve("demo");
        Files.createDirectories(target);
        Files.writeString(target.resolve("sentinel.txt"), "mine");

        assertThatThrownBy(() -> recording.clone(origin.toUri().toString(), target))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a git clone");

        assertThat(target.resolve("sentinel.txt")).exists();
        assertThat(commandsRun).isEmpty(); // not one git call, so nothing could have touched the folder
    }

    @Test
    // UC-WP-31
    void stalledCloneAbortsAndLeavesNothing() {
        // the fake leaves a half-written checkout behind, as a killed clone would
        GitService stalling = new GitCommandService((wd, command, timeout) -> {
            try {
                Files.createDirectories(Path.of(command.getLast()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return new CommandResult(-1, "Cloning into", true);
        }, FactoryHome.of(work), Duration.ofMillis(20).toString());
        Path target = projectsDir().resolve("slow");

        assertThatThrownBy(() -> stalling.clone(origin.toUri().toString(), target))
                .isInstanceOf(CloneFailedException.class)
                .hasMessageContaining("timed out");

        assertThat(target).doesNotExist();
        assertThat(projectsDir()).isDirectory();
    }

    @Test
    // UC-WP-29
    void cleanupOfAFailedCloneRefusesAnyTargetOutsideTheProjectsDir() {
        // the fake fails like a killed clone would: with a half-written checkout on disk
        GitService failing = new GitCommandService((wd, command, timeout) -> {
            try {
                Path partial = Path.of(command.getLast());
                Files.createDirectories(partial);
                Files.writeString(partial.resolve("partial.txt"), "half a checkout");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return new CommandResult(128, "fatal: repository not found", false);
        }, FactoryHome.of(work), Duration.ofMinutes(5).toString());
        Path stray = work.resolve("strays").resolve("demo"); // not <home>/projects/<name>

        assertThatThrownBy(() -> failing.clone("https://127.0.0.1:1/nope.git", stray))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refusing to touch");

        // refusing to delete means the half-written checkout is still there — loud, not tidy
        assertThat(stray.resolve("partial.txt")).exists();
    }

    /** A clone target is always {@code <home>/projects/<name>} — that is what the connector guards. */
    private Path projectsDir() {
        return work.resolve(FactoryHome.PROJECTS_DIR);
    }

    private GitService newGitService(CommandRunner runner) {
        return new GitCommandService(runner, FactoryHome.of(work), Duration.ofMinutes(5).toString());
    }

    private static void run(Path dir, String... command) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        CommandResult result = new ProcessCommandRunner().run(dir, List.of(command), Duration.ofMinutes(1));
        if (!result.ok()) {
            throw new IllegalStateException(command[0] + " " + command[1] + " failed: "
                    + new String(result.output().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        }
    }
}
