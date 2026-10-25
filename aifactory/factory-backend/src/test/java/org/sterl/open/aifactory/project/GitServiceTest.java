package org.sterl.open.aifactory.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import org.sterl.open.aifactory.project.CommandRunner.CommandResult;

/**
 * Real git through the {@link CommandRunner} seam — local {@code file://} repos only, plus the two
 * branches a real clone cannot show on demand (timeout, non-git target).
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
        git = new GitCommandService(commands, Duration.ofMinutes(5).toString());
        origin = work.resolve("origin");
        run(origin, "git", "init", "-q", ".");
        run(origin, "git", "-c", "user.email=f@actory.test", "-c", "user.name=Factory",
                "commit", "-q", "--allow-empty", "-m", "first");
    }

    @Test
    // UC-WP-2
    void clonesLocalRepoIntoTarget() {
        Path target = work.resolve("clones/demo");

        boolean cloned = git.clone(origin.toUri().toString(), target);

        assertThat(cloned).isTrue();
        assertThat(target.resolve(".git")).isDirectory();
    }

    @Test
    // UC-WP-3
    void existingCloneIsReusedAndNotTouched() throws IOException {
        Path target = work.resolve("clones/demo");
        git.clone(origin.toUri().toString(), target);
        Files.writeString(target.resolve("sentinel.txt"), "mine");

        boolean cloned = git.clone(origin.toUri().toString(), target);

        assertThat(cloned).isFalse();
        assertThat(target.resolve("sentinel.txt")).exists();
    }

    @Test
    // UC-WP-4
    void invalidUrlFailsFastAndLeavesNoPartialClone() {
        Path target = work.resolve("clones/nope");

        assertThatThrownBy(() -> git.clone("https://127.0.0.1:1/nope.git", target))
                .isInstanceOf(CloneFailedException.class)
                .satisfies(e -> assertThat(((CloneFailedException) e).gitOutput()).isNotBlank());

        assertThat(target).doesNotExist();
    }

    @Test
    void nonGitFolderOfThatNameIsRejected() throws IOException {
        Path target = work.resolve("clones/demo");
        Files.createDirectories(target);

        assertThatThrownBy(() -> git.clone(origin.toUri().toString(), target))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a git clone");
    }

    @Test
    void timedOutCloneReportsTheTimeoutAndCleansUp() {
        // the fake leaves a half-written checkout behind, as a killed clone would
        GitService stalling = new GitCommandService((wd, command, timeout) -> {
            try {
                Files.createDirectories(Path.of(command.getLast()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return new CommandResult(-1, "Cloning into", true);
        }, Duration.ofMillis(20).toString());
        Path target = work.resolve("clones/slow");

        assertThatThrownBy(() -> stalling.clone(origin.toUri().toString(), target))
                .isInstanceOf(CloneFailedException.class)
                .hasMessageContaining("timed out");

        assertThat(target).doesNotExist();
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
