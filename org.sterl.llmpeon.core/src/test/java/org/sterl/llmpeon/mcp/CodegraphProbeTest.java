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
 * R-CG-2 / R-CG-9: the probe is pure path-based and read-only — a regular-file check for the
 * binary (default install, then PATH) and for {@code .codegraph/codegraph.db}; a bare
 * {@code .codegraph/} directory (init transient) is negative, nothing is written.
 */
class CodegraphProbeTest {

    @TempDir
    Path temp;

    private DefaultCodegraphProbe probe(Path home, String pathEnv) {
        return new DefaultCodegraphProbe(home, pathEnv);
    }

    @Test
    void givenBinaryInDefaultInstall_whenFindBinary_thenFound() throws IOException {
        // GIVEN the binary at the default install location
        var home = temp.resolve("home");
        var binary = home.resolve(".local").resolve("bin").resolve("codegraph");
        Files.createDirectories(binary.getParent());
        Files.createFile(binary);

        // WHEN the probe looks for the binary
        var found = probe(home, "/usr/bin:/bin").findBinary();

        // THEN the default install wins
        assertThat(found).contains(binary);
    }

    @Test
    void givenBinaryOnlyInPath_whenFindBinary_thenFoundInSecondEntry() throws IOException {
        // GIVEN no default install, the binary only in the second PATH entry
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
        var found = probe(home, pathEnv).findBinary();

        // THEN the first hit (second entry) wins
        assertThat(found).contains(binary);
    }

    @Test
    void givenNoBinaryAnywhere_whenFindBinary_thenEmpty() throws IOException {
        // GIVEN an empty home and PATH entries without the binary (one blank entry)
        var home = temp.resolve("home-none");
        Files.createDirectories(home);
        var entry = temp.resolve("p-none");
        Files.createDirectories(entry);

        // WHEN the probe looks for the binary
        var found = probe(home, String.join(File.pathSeparator, entry.toString(), "")).findBinary();

        // THEN nothing is found — no exception, no default
        assertThat(found).isEmpty();
    }

    @Test
    void givenIndexFile_whenHasIndex_thenTrue() throws IOException {
        // GIVEN a project with .codegraph/codegraph.db as a regular file
        var project = temp.resolve("project");
        var db = project.resolve(".codegraph").resolve("codegraph.db");
        Files.createDirectories(db.getParent());
        Files.createFile(db);

        // WHEN the probe checks the index
        var hasIndex = probe(temp.resolve("home"), "").hasIndex(project);

        // THEN the index exists
        assertThat(hasIndex).isTrue();
    }

    @Test
    void givenOnlyIndexDirectory_whenHasIndex_thenFalse() throws IOException {
        // GIVEN a project with a bare .codegraph/ directory (init transient, R-CG-2)
        var project = temp.resolve("project-transient");
        Files.createDirectories(project.resolve(".codegraph"));

        // WHEN the probe checks the index
        var hasIndex = probe(temp.resolve("home"), "").hasIndex(project);

        // THEN a directory alone is negative — no false positive during init
        assertThat(hasIndex).isFalse();
    }

    @Test
    void givenProbeReadsOnly_whenProbing_thenDirectoryUnchanged() throws IOException {
        // GIVEN a project with a full index and the binary installed
        var home = temp.resolve("home-ro");
        var binary = home.resolve(".local").resolve("bin").resolve("codegraph");
        Files.createDirectories(binary.getParent());
        Files.createFile(binary);
        var project = temp.resolve("project-ro");
        var db = project.resolve(".codegraph").resolve("codegraph.db");
        Files.createDirectories(db.getParent());
        Files.createFile(db);
        var before = snapshot(project);

        // WHEN the probe checks binary and index
        var p = probe(home, "");
        assertThat(p.findBinary()).contains(binary);
        assertThat(p.hasIndex(project)).isTrue();

        // THEN the directory content is unchanged (R-CG-9: no write actions)
        assertThat(snapshot(project)).isEqualTo(before);
    }

    /** All relative file paths under the root — the probe must not add, remove or rename any. */
    private List<String> snapshot(Path root) throws IOException {
        var paths = new ArrayList<String>();
        try (var walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(p -> paths.add(root.relativize(p).toString()));
        }
        paths.sort(String::compareTo);
        return paths;
    }
}
