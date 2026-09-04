package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.junit.After;
import org.junit.Test;

public class PeonTestFixtureResolveTest {

    private Path tempDir;

    @After
    public void deleteTempDir() throws IOException {
        if (tempDir == null) return;
        try (var paths = Files.walk(tempDir)) {
            paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }

    @Test
    public void propertyWinsOverDefault() throws IOException {
        tempDir = Files.createTempDirectory("peon-fixture-");
        var propertyFixture = createFixture(tempDir.resolve("property-fixture"));
        var bundleDir = Files.createDirectories(tempDir.resolve("repo/module/target/classes"));
        createFixture(tempDir.resolve("repo/test_project"));

        var result = PeonTestFixture.resolve(propertyFixture.toString(), bundleDir.toFile());

        assertEquals(propertyFixture.toAbsolutePath().normalize().toFile(), result);
    }

    @Test
    public void resolvesFromBundleLocation() throws IOException {
        tempDir = Files.createTempDirectory("peon-fixture-");
        var fixture = createFixture(tempDir.resolve("repo/test_project"));
        var bundleDir = Files.createDirectories(tempDir.resolve("repo/module/target/classes"));

        var result = PeonTestFixture.resolve(null, bundleDir.toFile());

        assertEquals(fixture.toAbsolutePath().normalize().toFile(), result);
    }

    @Test
    public void missingFixtureFails() throws IOException {
        tempDir = Files.createTempDirectory("peon-fixture-");
        var missing = tempDir.resolve("missing").toAbsolutePath().normalize();

        var error = assertThrows(IllegalStateException.class,
                () -> PeonTestFixture.resolve(missing.toString(), tempDir.toFile()));

        assertEquals("test fixture not found: " + missing + " (set -Dpeon.test.project=…)", error.getMessage());
    }

    private static Path createFixture(Path fixture) throws IOException {
        Files.createDirectories(fixture);
        Files.createFile(fixture.resolve(".project"));
        return fixture;
    }
}
