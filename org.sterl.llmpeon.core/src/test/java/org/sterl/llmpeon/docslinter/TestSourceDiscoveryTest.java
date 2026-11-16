package org.sterl.llmpeon.docslinter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** R-DL-28: the production visitor decides pruning per directory. */
@Timeout(value = 1)
class TestSourceDiscoveryTest {

    @TempDir
    Path tmp;

    // UC-DL-101
    @ParameterizedTest
    @ValueSource(strings = { "node_modules", "target", "bin", "build", "out", ".git", ".settings", ".gradle" })
    void foreignDescendantReturnsSkipSubtree(String name) throws IOException {
        Path root = Files.createDirectories(tmp.resolve("root"));
        Path child = Files.createDirectories(root.resolve("sub").resolve(name));

        var result = visitor(root).preVisitDirectory(child, attrs(child));

        assertThat(result).isEqualTo(FileVisitResult.SKIP_SUBTREE);
    }

    // UC-DL-102
    @ParameterizedTest
    @ValueSource(strings = { "build", "node_modules" })
    void selectedForeignRootReturnsContinue(String name) throws IOException {
        Path root = Files.createDirectories(tmp.resolve("x").resolve(name));
        Path sameRoot = tmp.resolve("x").resolve("..").resolve("x").resolve(name).normalize();

        var result = visitor(root).preVisitDirectory(sameRoot, attrs(root));

        assertThat(sameRoot).isNotSameAs(root).isEqualTo(root);
        assertThat(result).isEqualTo(FileVisitResult.CONTINUE);
    }

    @Test
    void visitFailureRethrowsOriginalIoException() throws IOException {
        Path root = Files.createDirectories(tmp.resolve("root"));
        var failure = new IOException("boom");

        var thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> visitor(root).visitFileFailed(root.resolve("f"), failure));

        assertThat(thrown).isSameAs(failure);
    }

    private static TestSourceDiscovery visitor(Path root) {
        return new TestSourceDiscovery(root, root, List.of(), List.of());
    }

    private static BasicFileAttributes attrs(Path p) throws IOException {
        return Files.readAttributes(p, BasicFileAttributes.class);
    }
}
