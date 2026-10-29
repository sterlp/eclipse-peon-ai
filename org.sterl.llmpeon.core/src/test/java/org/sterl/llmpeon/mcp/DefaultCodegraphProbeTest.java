package org.sterl.llmpeon.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * R-CG-2 / R-CG-9: the probe is pure path-based and read-only — the binary is a start
 * HINT (default install, then the user tool environment's PATH), the index search walks UP
 * from the project to the Git root (the directory holding {@code .codegraph/codegraph.db}
 * is the workDir; a bare {@code .codegraph/} directory stays init-transient). No process
 * is started, nothing is written.
 */
class DefaultCodegraphProbeTest {

    @TempDir
    Path temp;

    private DefaultCodegraphProbe probe(Path home, String userPathPrefix, String systemPath) {
        return new DefaultCodegraphProbe(home, userPathPrefix, systemPath);
    }

    private static void createIndex(Path dir) throws IOException {
        var db = dir.resolve(".codegraph").resolve("codegraph.db");
        Files.createDirectories(db.getParent());
        Files.createFile(db);
    }

    // ── binary: hint, not a gate ──

    @Test
    void givenBinaryInDefaultInstall_whenFindBinary_thenFound() throws IOException {
        // GIVEN the binary at the default install location
        var home = temp.resolve("home");
        var binary = home.resolve(".local").resolve("bin").resolve("codegraph");
        Files.createDirectories(binary.getParent());
        Files.createFile(binary);

        // WHEN the probe looks for the binary
        var found = probe(home, "", "/usr/bin:/bin").findBinary();

        // THEN the default install wins
        assertThat(found).contains(binary);
    }

    @Test
    void givenBinaryOnlyInUserPath_whenFindBinary_thenFound() throws IOException {
        // GIVEN no default install, the binary only in a user-env PATH entry (NOT in the system PATH)
        var home = temp.resolve("home-userpath");
        Files.createDirectories(home);
        var userEntry = temp.resolve("user-bin");
        var sysEntry = temp.resolve("sys-bin");
        Files.createDirectories(userEntry);
        Files.createDirectories(sysEntry);
        var binary = userEntry.resolve("codegraph");
        Files.createFile(binary);

        // WHEN the probe scans the user environment's PATH (the same one ShellTool uses, R-CG-2)
        var found = probe(home, userEntry.toString(), sysEntry.toString()).findBinary();

        // THEN it is found although System.getenv("PATH") alone would miss it
        assertThat(found).contains(binary);
    }

    @Test
    void givenBinaryOnlyInSystemPath_whenFindBinary_thenFoundInSecondEntry() throws IOException {
        // GIVEN no default install, the binary only in the second system PATH entry
        var home = temp.resolve("home-empty");
        Files.createDirectories(home);
        var entry1 = temp.resolve("p1");
        var entry2 = temp.resolve("p2");
        var entry3 = temp.resolve("p3");
        Files.createDirectories(entry1);
        Files.createDirectories(entry3);
        Files.createDirectories(entry2);
        var binary = entry2.resolve("codegraph");
        Files.createFile(binary);
        var pathEnv = String.join(File.pathSeparator, entry1.toString(), entry2.toString(), entry3.toString());

        // WHEN the probe scans the PATH
        var found = probe(home, "", pathEnv).findBinary();

        // THEN the first hit (second entry) wins
        assertThat(found).contains(binary);
    }

    @Test
    void givenNoBinaryAnywhere_whenFindBinary_thenEmpty() throws IOException {
        // GIVEN an empty home and user/system PATH entries without the binary (one blank entry)
        var home = temp.resolve("home-none");
        Files.createDirectories(home);
        var entry = temp.resolve("p-none");
        Files.createDirectories(entry);

        // WHEN the probe looks for the binary
        var found = probe(home, "", String.join(File.pathSeparator, entry.toString(), "")).findBinary();

        // THEN nothing is found — no exception, no default
        assertThat(found).isEmpty();
    }

    // ── index: upward to the Git root, the found directory is the workDir ──

    @Test
    void prefersProjectIndex() throws IOException {
        // GIVEN the index in BOTH the project folder and its Git-root parent
        var root = temp.resolve("root");
        var project = root.resolve("module");
        Files.createDirectories(root.resolve(".git"));
        createIndex(root);
        createIndex(project);

        // WHEN the probe searches from the project
        var workDir = probe(temp.resolve("home"), "", "").findIndexWorkDir(project);

        // THEN the closest index wins — the project folder itself
        assertThat(workDir).contains(project);
    }

    @Test
    void findsIndexInGitRoot() throws IOException {
        // GIVEN the index in the Git root, the project is a module one level down
        var root = temp.resolve("git-root");
        var project = root.resolve("module");
        Files.createDirectories(root.resolve(".git"));
        createIndex(root);
        Files.createDirectories(project);

        // WHEN the probe searches from the module
        var workDir = probe(temp.resolve("home"), "", "").findIndexWorkDir(project);

        // THEN the Git root is found and is the workDir (R-CG-2)
        assertThat(workDir).contains(root);
    }

    @Test
    void givenSubmoduleProject_whenFindIndexWorkDir_thenWalksPastGitFileToSuperproject() throws IOException {
        // GIVEN a submodule (.git as a FILE) whose superproject holds the index at its root
        var superRoot = temp.resolve("super");
        var submodule = superRoot.resolve("sub");
        Files.createDirectories(superRoot.resolve(".git"));
        createIndex(superRoot);
        Files.createDirectories(submodule);
        Files.writeString(submodule.resolve(".git"), "gitdir: ../.git/modules/sub");

        // WHEN the probe searches from the submodule
        var workDir = probe(temp.resolve("home"), "", "").findIndexWorkDir(submodule);

        // THEN the walk continues past the submodule's .git file to the superproject root
        assertThat(workDir).contains(superRoot);
    }

    @Test
    void givenIndexAboveGitRoot_whenFindIndexWorkDir_thenStopsAtGitRoot() throws IOException {
        // GIVEN a .codegraph index ABOVE the Git root (in the repo's parent)
        var outer = temp.resolve("outer");
        var root = outer.resolve("repo");
        var project = root.resolve("module");
        createIndex(outer);
        Files.createDirectories(root.resolve(".git"));
        Files.createDirectories(project);

        // WHEN the probe searches from the module
        var workDir = probe(temp.resolve("home"), "", "").findIndexWorkDir(project);

        // THEN the search stops at the Git root — nothing above it counts
        assertThat(workDir).isEmpty();
    }

    @Test
    void givenOnlyIndexDirectory_whenFindIndexWorkDir_thenEmpty() throws IOException {
        // GIVEN a project with a bare .codegraph/ directory (init transient, R-CG-2)
        var project = temp.resolve("project-transient");
        Files.createDirectories(project.resolve(".codegraph"));

        // WHEN the probe checks the index
        var workDir = probe(temp.resolve("home"), "", "").findIndexWorkDir(project);

        // THEN a directory alone is negative — no false positive during init
        assertThat(workDir).isEmpty();
    }

    @Test
    void givenProbeReadsOnly_whenProbing_thenDirectoryUnchanged() throws IOException {
        // GIVEN a project with a full index in the Git root and the binary installed
        var home = temp.resolve("home-ro");
        var binary = home.resolve(".local").resolve("bin").resolve("codegraph");
        Files.createDirectories(binary.getParent());
        Files.createFile(binary);
        var root = temp.resolve("root-ro");
        var project = root.resolve("module");
        Files.createDirectories(root.resolve(".git"));
        createIndex(root);
        Files.createDirectories(project);
        var before = snapshot(root);

        // WHEN the probe checks the binary and the index
        var p = probe(home, "", "");
        assertThat(p.findBinary()).contains(binary);
        assertThat(p.findIndexWorkDir(project)).contains(root);

        // THEN the directory content is unchanged (R-CG-9: no write actions)
        assertThat(snapshot(root)).isEqualTo(before);
    }

    /** All relative file paths under the root — the probe must not add, remove or rename any. */
    @SuppressWarnings("null")
    private List<String> snapshot(Path root) throws IOException {
        var paths = new ArrayList<String>();
        try (var walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(p -> paths.add(root.relativize(p).toString()));
        }
        paths.sort(String::compareTo);
        return paths;
    }
}
