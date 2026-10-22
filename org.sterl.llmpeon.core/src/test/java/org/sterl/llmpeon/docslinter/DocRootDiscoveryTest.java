package org.sterl.llmpeon.docslinter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocRootDiscoveryTest {

    @TempDir
    Path root;

    // UC-DL-74
    @Test
    void discoversModuleDocRoots() throws IOException {
        // GIVEN docs/, a/docs/ and b/c/docs/ each carrying a Markdown file
        mkDocDir("docs");
        mkDocDir("a/docs");
        mkDocDir("b/c/docs");

        // WHEN
        var found = DocRootDiscovery.discover(root);

        // THEN all three are roots — sorted, with "docs" first
        assertThat(found.roots()).containsExactly("docs", "a/docs", "b/c/docs");
    }

    // UC-DL-75
    @Test
    void keepsRootDocsFirst() throws IOException {
        // GIVEN a root docs dir and a module docs dir that sorts before it
        mkDocDir("a/docs");
        mkDocDir("docs");

        // WHEN
        var found = DocRootDiscovery.discover(root);

        // THEN "docs" leads, the rest stays sorted
        assertThat(found.roots()).containsExactly("docs", "a/docs");
    }

    // UC-DL-76
    @Test
    void skipsForeignTrees() throws IOException {
        // GIVEN docs directories inside build/vendor trees, each with Markdown
        mkDocDir("node_modules/docs");
        mkDocDir("target/docs");
        mkDocDir("bin/docs");

        // WHEN
        var found = DocRootDiscovery.discover(root);

        // THEN none of them is a root — those trees are never entered
        assertThat(found.roots()).isEmpty();
    }

    // UC-DL-77
    @Test
    void ignoresDocsDirWithoutMarkdown() throws IOException {
        // GIVEN a docs directory without a single .md file
        Files.createDirectories(root.resolve("x/docs"));

        // WHEN
        var found = DocRootDiscovery.discover(root);

        // THEN it is no root but disclosed as ignored
        assertThat(found.roots()).isEmpty();
        assertThat(found.ignored()).containsExactly("x/docs");
    }

    // UC-DL-78
    @Test
    void respectsMaxDepth() throws IOException {
        // GIVEN a docs directory three levels below the root — beyond the two-level limit
        mkDocDir("x/y/z/docs");

        // WHEN
        var found = DocRootDiscovery.discover(root);

        // THEN it is not found
        assertThat(found.roots()).isEmpty();
    }

    private void mkDocDir(String relativeDir) throws IOException {
        Path dir = root.resolve(relativeDir);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("a.md"), "# doc\n");
    }
}
