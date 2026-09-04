package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Test;

public class TestFixtureIntegrityTest {

    private final Path fixture = PeonTestFixture.dir().toPath();

    @Test
    public void lineFixtureHasExactly120NumberedLines() throws IOException {
        List<String> lines = Files.readAllLines(fixture.resolve("data/lines-120.txt"), StandardCharsets.UTF_8);

        assertEquals(120, lines.size());
        for (int i = 0; i < lines.size(); i++) {
            assertEquals("line " + (i + 1), lines.get(i));
        }
    }

    @Test
    public void grepAndMetaCharacterFixturesRemainStable() throws IOException {
        List<String> grepLines = Files.readAllLines(
                fixture.resolve("src/org/sterl/fixture/GrepTarget.java"), StandardCharsets.UTF_8);
        String metaChars = Files.readString(
                fixture.resolve("src/org/sterl/fixture/MetaChars.java"), StandardCharsets.UTF_8);

        assertEquals(3, grepLines.stream().filter(line -> line.contains("grepMe")).count());
        assertEquals(1, occurrences(metaChars, "C++"));
        assertEquals(1, occurrences(metaChars, "a.b"));
        assertEquals(1, occurrences(metaChars, "foo(bar"));
    }

    @Test
    public void wildcardAndNestedSourcesExist() {
        assertTrue(Files.isRegularFile(fixture.resolve("src/org/sterl/fixture/Alpha.java")));
        assertTrue(Files.isRegularFile(fixture.resolve("src/org/sterl/fixture/AlphaBeta.java")));
        assertTrue(Files.isRegularFile(fixture.resolve("src/org/sterl/fixture/Alphabet.java")));
        assertTrue(Files.isRegularFile(fixture.resolve("src/org/sterl/fixture/sub/Nested.java")));
    }

    @Test
    public void encodedTextFixturesHaveExpectedContent() throws IOException {
        assertEquals("äüß Ö ⚡", Files.readString(
                fixture.resolve("data/utf-8-test.txt"), StandardCharsets.UTF_8));
        assertEquals("äüß Ö", Files.readString(
                fixture.resolve("data/iso-test.txt"), StandardCharsets.ISO_8859_1));
    }

    @Test
    public void pomContainsProjectElement() throws IOException {
        String pom = Files.readString(fixture.resolve("pom.xml"), StandardCharsets.UTF_8);

        assertTrue(pom.contains("<project"));
        assertTrue(pom.contains("</project>"));
    }

    @Test
    public void grepTypeFixturesExist() throws IOException {
        String dockerfile = Files.readString(fixture.resolve("Dockerfile"), StandardCharsets.UTF_8);
        String unknownType = Files.readString(fixture.resolve("data/notes.peonx"), StandardCharsets.UTF_8);

        assertTrue(dockerfile.contains("dockerGrepMe"));
        assertTrue(unknownType.contains("dockerGrepMe"));
        assertTrue(unknownType.contains("peonx-only-token"));
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
