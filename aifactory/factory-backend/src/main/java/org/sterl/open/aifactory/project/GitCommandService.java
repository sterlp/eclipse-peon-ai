package org.sterl.open.aifactory.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.sterl.open.aifactory.project.CommandRunner.CommandResult;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * Git through the {@link CommandRunner} CLI — no Jgit on purpose (AF-ADR-001 keeps
 * {@code org.eclipse.*} out of factory code) and public HTTPS repos only.
 */
@Service
public class GitCommandService implements GitService {

    private static final Logger LOG = LoggerFactory.getLogger(GitCommandService.class);

    private final CommandRunner commands;
    private final FactoryHome home;
    private final Duration cloneTimeout;

    public GitCommandService(CommandRunner commands, FactoryHome home,
            @Value("${aifactory.git.timeout:PT5M}") String cloneTimeout) {
        this.commands = commands;
        this.home = home;
        this.cloneTimeout = Duration.parse(cloneTimeout);
    }

    @Override
    public boolean clone(String gitUrl, Path target) {
        if (Files.exists(target)) {
            if (Files.isDirectory(target.resolve(".git"))) {
                return false; // already cloned -> reuse, never touched (R-WP-3)
            }
            throw new IllegalStateException(
                    "target folder " + target + " exists but is not a git clone");
        }
        createParentDirectories(target);
        CommandResult result = commands.run(target.getParent(),
                List.of("git", "clone", "--", gitUrl, target.toString()), cloneTimeout);
        if (!result.ok()) {
            deleteQuietly(target); // no partial clone may survive (R-WP-4)
            String output = result.timedOut()
                    ? "git clone timed out after " + cloneTimeout
                    : lastLines(result.output());
            LOG.info("clone of {} into {} failed: {}", gitUrl, target, output);
            throw new CloneFailedException(gitUrl, target, output);
        }
        return true;
    }

    /** git refuses to clone into a missing directory — the projects dir may not exist yet. */
    private static void createParentDirectories(Path target) {
        Path parent = target.getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new IllegalStateException("cannot create " + parent + ": " + e.getMessage(), e);
        }
    }

    /** git writes the reason on its last lines; the head is the progress noise nobody reads. */
    private static String lastLines(String output) {
        if (output == null || output.isBlank()) {
            return "git clone produced no output";
        }
        List<String> lines = output.lines().filter(line -> !line.isBlank()).toList();
        int from = Math.max(0, lines.size() - 6);
        return String.join(System.lineSeparator(), lines.subList(from, lines.size()));
    }

    /**
     * A partial clone is ours to remove, so this walks nothing the containment rule does not vouch
     * for — refusing loudly beats deleting a directory the registry never claimed (UC-WP-29).
     */
    private void deleteQuietly(Path target) {
        Path checkout = ProjectPaths.requireInsideProjectsDir(home.projectsDir(), target);
        if (!Files.exists(checkout)) {
            return;
        }
        try (Stream<Path> tree = Files.walk(checkout)) {
            for (Path path : tree.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            LOG.info("could not remove partial clone {}", checkout);
        }
    }
}
