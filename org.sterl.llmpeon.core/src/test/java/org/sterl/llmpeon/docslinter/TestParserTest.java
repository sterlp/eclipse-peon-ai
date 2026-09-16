package org.sterl.llmpeon.docslinter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestParserTest {

    private static final Pattern ID_PATTERN = Pattern.compile(DocsLinterTool.DEFAULT_ID_PATTERN);
    private final TestParser parser = new TestParser();

    @TempDir
    Path tempDir;

    @Test
    void assignsMultipleIdsToFollowingMethod() throws IOException {
        // UC-DL-9
        writeTest("Test.java", """
                // UC-DL-1, UC-DL-2
                void testIt() {}""");

        var evidence = parser.parse(file("Test.java"), "Test.java", ID_PATTERN);

        assertThat(evidence).hasSize(2);
        assertThat(evidence).extracting(TestParser.TestEvidence::id)
                .containsExactly("UC-DL-1", "UC-DL-2");
        assertThat(evidence).allMatch(e -> "testIt".equals(e.methodName()));
        assertThat(evidence).allMatch(e -> e.line() == 1);
    }

    @Test
    void ignoresIdInNarrativeComment() throws IOException {
        // UC-DL-10 — UC-DL-1 matches the pattern and must be ignored in narrative text
        writeTest("Test.java", """
                // This tests UC-DL-1 for edge cases
                void testIt() {}""");

        var evidence = parser.parse(file("Test.java"), "Test.java", ID_PATTERN);

        assertThat(evidence).isEmpty();
    }

    @Test
    void ignoresIdInBlockComment() throws IOException {
        writeTest("Test.java", """
                /* UC-DL-1 in block */
                void testIt() {}""");

        var evidence = parser.parse(file("Test.java"), "Test.java", ID_PATTERN);

        assertThat(evidence).isEmpty();
    }

    @Test
    void acceptsPureIdCommentWithoutFollowingMethod() throws IOException {
        // UC-DL-34 / UC-DL-36: the ID line itself is evidence; unknown syntax is allowed
        writeTest("Test.java", """
                // UC-DL-1
                int x = 1;""");

        var evidence = parser.parse(file("Test.java"), "Test.java", ID_PATTERN);

        assertThat(evidence).hasSize(1);
        assertThat(evidence.get(0).id()).isEqualTo("UC-DL-1");
        assertThat(evidence.get(0).methodName()).isNull();
    }

    @Test
    void acceptsPureIdCommentAtEof() throws IOException {
        writeTest("Test.java", "// UC-DL-1");

        var evidence = parser.parse(file("Test.java"), "Test.java", ID_PATTERN);

        assertThat(evidence).hasSize(1);
        assertThat(evidence.get(0).id()).isEqualTo("UC-DL-1");
        assertThat(evidence.get(0).methodName()).isNull();
    }

    @Test
    void acceptsEmptyLinesBeforeMethod() throws IOException {
        writeTest("Test.java", """
                // UC-DL-1

                void testIt() {}""");

        var evidence = parser.parse(file("Test.java"), "Test.java", ID_PATTERN);

        assertThat(evidence).hasSize(1);
        assertThat(evidence.get(0).methodName()).isEqualTo("testIt");
    }

    @Test
    void acceptsAnnotationBeforeMethod() throws IOException {
        writeTest("Test.java", """
                // UC-DL-1
                @Test
                void testIt() {}""");

        var evidence = parser.parse(file("Test.java"), "Test.java", ID_PATTERN);

        assertThat(evidence).hasSize(1);
        assertThat(evidence.get(0).methodName()).isEqualTo("testIt");
    }

    @Test
    void secondCommentEndsContextSearchButKeepsEvidence() throws IOException {
        writeTest("Test.java", """
                // UC-DL-1
                // Some other comment
                void methodA() {}

                // UC-DL-2
                void methodB() {}""");

        var evidence = parser.parse(file("Test.java"), "Test.java", ID_PATTERN);

        assertThat(evidence).hasSize(2);
        assertThat(evidence).extracting(TestParser.TestEvidence::id)
                .containsExactly("UC-DL-1", "UC-DL-2");
        assertThat(evidence.get(0).methodName()).isNull();
        assertThat(evidence.get(1).methodName()).isEqualTo("methodB");
    }

    @Test
    void recognizesPythonStyleComment() throws IOException {
        writeTest("tests.py", """
                # UC-DL-2
                def test_python(): pass""");

        var evidence = parser.parse(file("tests.py"), "tests.py", ID_PATTERN);

        assertThat(evidence).hasSize(1);
        assertThat(evidence.get(0).methodName()).isEqualTo("test_python");
    }

    @Test
    void recognizesGoStyleMethod() throws IOException {
        writeTest("test.go", """
                // UC-DL-3
                func TestGo(t *testing.T) {}""");

        var evidence = parser.parse(file("test.go"), "test.go", ID_PATTERN);

        assertThat(evidence).hasSize(1);
        assertThat(evidence.get(0).methodName()).isEqualTo("TestGo");
    }


    @Test
    void preservesEveryConsecutiveIdCommentAndOptionalMethodContext() throws IOException {
        // UC-DL-34 / UC-DL-36: each pure ID line is independent evidence; method context
        // is optional enrichment from the following lines, never a filter or overwrite.
        writeTest("Test.java", """
                // UC-DL-1
                // UC-DL-2
                void testIt() {}""");

        var evidence = parser.parse(file("Test.java"), "Test.java", ID_PATTERN);

        assertThat(evidence).hasSize(2);
        assertThat(evidence).extracting(TestParser.TestEvidence::id)
                .containsExactly("UC-DL-1", "UC-DL-2");
        assertThat(evidence).extracting(TestParser.TestEvidence::line)
                .containsExactly(1, 2);
        assertThat(evidence.get(0).methodName()).isNull();
        assertThat(evidence.get(1).methodName()).isEqualTo("testIt");
    }

    @Test
    void recognizesSqlCommentPrefix() throws IOException {
        // UC-DL-35
        writeTest("test.sql", """
                -- UC-DL-35
                SELECT 1;""");

        var evidence = parser.parse(file("test.sql"), "test.sql", ID_PATTERN);

        assertThat(evidence).hasSize(1);
        assertThat(evidence.get(0).id()).isEqualTo("UC-DL-35");
        assertThat(evidence.get(0).methodName()).isNull();
    }

    private void writeTest(String name, String content) throws IOException {
        Files.writeString(tempDir.resolve(name), content);
    }

    private Path file(String name) {
        return tempDir.resolve(name);
    }
}
