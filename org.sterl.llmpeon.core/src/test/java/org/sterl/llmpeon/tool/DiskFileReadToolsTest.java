package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.model.SimpleMessage;
import org.sterl.llmpeon.tool.tools.DiskFileReadTool;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

class DiskFileReadToolsTest {

    @TempDir
    Path tempDir;

    DiskFileReadTool tool;

    @BeforeEach
    void setUp() {
        tool = new DiskFileReadTool(tempDir);
    }

    @Test
    void readDiskFile_existingFile() throws IOException {
        Files.writeString(tempDir.resolve("hello.txt"), "world");
        assertTrue(tool.diskReadFile("hello.txt", 0, 0).contains("world"),
                tool.diskReadFile("hello.txt", 0, 0));
    }

    @Test
    void readDiskFile_missingFile() {
        try {
            tool.diskReadFile("missing.txt", 0, 0);
            fail("Missing IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("File not found"));
            assertTrue(e.getMessage().contains("missing.txt"));
        }
    }

    @Test
    void searchDiskFiles_findsMatch() throws IOException {
        // GIVEN
        Files.createDirectories(tempDir.resolve("foo"));
        Files.createDirectories(tempDir.resolve("bar"));
        Files.writeString(tempDir.resolve("foo/FooController.java"), "class Foo {}");
        Files.writeString(tempDir.resolve("bar/BarController.java"), "class Bar {}");
        // WHEN
        String result = tool.diskSearchFiles("FooController", 0);
        // THEN
        assertTrue(result.contains("FooController.java"));
        assertFalse(result.contains("BarController.java"));
        
        // WHEN
        result = tool.diskSearchFiles("*Controller*.java", 0);
        // THEN
        assertTrue(result.contains("FooController.java"));
        assertTrue(result.contains("BarController.java"));
        
        // WHEN
        result = tool.diskSearchFiles("**/FooController.java", 0);
        // THEN
        assertThat(result).contains("FooController.java");
        assertThat(result).doesNotContain("BarController.java");
    }

    @Test
    void searchDiskFiles_globExtension() throws IOException {
        Files.writeString(tempDir.resolve("README.md"), "docs");
        Files.writeString(tempDir.resolve("notes.md"), "notes");
        Files.writeString(tempDir.resolve("Other.java"), "code");
        String result = tool.diskSearchFiles("*.md", 0);
        assertTrue(result.contains("README.md"), "should find README.md");
        assertTrue(result.contains("notes.md"), "should find notes.md");
        assertFalse(result.contains("Other.java"), "should not find .java file");
    }

    @Test
    void searchDiskFiles_globPrefix() throws IOException {
        // GIVEN
        Files.writeString(tempDir.resolve("FooController.java"), "");
        Files.writeString(tempDir.resolve("FooService.java"), "");
        Files.writeString(tempDir.resolve("BarHelper.java"), "");
        // WHEN
        String result = tool.diskSearchFiles("Foo*", 0);
        // THEN
        assertTrue(result.contains("FooController.java"));
        assertTrue(result.contains("FooService.java"));
        assertFalse(result.contains("BarHelper.java"));
        
        // AND no error
        tool.diskReadFile(result.split("\n")[0], 0, 0);
    }

    @Test
    void searchDiskFiles_limitRestrictsResults() throws IOException {
        // GIVEN - three files
        Files.writeString(tempDir.resolve("A.java"), "");
        Files.writeString(tempDir.resolve("B.java"), "");
        Files.writeString(tempDir.resolve("C.java"), "");

        // unlimited returns all three
        assertEquals(3, countHits(tool.diskSearchFiles("*.java", 0)));

        // limit=1 returns exactly one + cap disclosure
        String limited1 = tool.diskSearchFiles("*.java", 1);
        assertEquals(1, countHits(limited1));
        assertTrue(limited1.contains("capped at 1 — narrow your search"), limited1);

        // limit=2 returns exactly two + cap disclosure
        String limited2 = tool.diskSearchFiles("*.java", 2);
        assertEquals(2, countHits(limited2));
        assertTrue(limited2.contains("capped at 2 — narrow your search"), limited2);
    }

    @Test
    void searchDiskFilesDisclosesCap() throws IOException {
        // UC-OD-2
        // GIVEN - 80 matching files, default limit 50
        for (int i = 0; i < 80; i++) {
            Files.writeString(tempDir.resolve(String.format("f%02d.java", i)), "");
        }

        // WHEN
        String result = tool.diskSearchFiles("f*.java", null);

        // THEN - 50 hits and the cap is named
        assertEquals(50, countHits(result));
        assertTrue(result.contains("capped at 50 — narrow your search"), result);
    }

    @Test
    void searchDiskFilesUnlimitedNoDisclosure() throws IOException {
        // UC-OD-3
        // GIVEN - 30 matching files
        for (int i = 0; i < 30; i++) {
            Files.writeString(tempDir.resolve(String.format("g%02d.java", i)), "");
        }

        // WHEN
        String result = tool.diskSearchFiles("g*.java", 0);

        // THEN - all 30 hits, nothing was cropped → no disclosure
        assertEquals(30, countHits(result));
        assertFalse(result.contains("capped at"), result);
    }

    private static long countHits(String result) {
        return result.lines().filter(l -> l.endsWith(".java")).count();
    }

    @Test
    void searchDiskFiles_emptyQuery() {
        assertThrows(IllegalArgumentException.class, () -> tool.diskSearchFiles("", 0));
        assertThrows(IllegalArgumentException.class, () -> tool.diskSearchFiles(null, 0));
    }

    @Test
    void readDiskFile_absolutePath() throws IOException {
        Path abs = tempDir.resolve("abs.txt");
        Files.writeString(abs, "absolute");
        assertTrue(tool.diskReadFile(abs.toString(), 0, 0).contains("absolute"),
                tool.diskReadFile(abs.toString(), 0, 0));
    }

    // TODO: https://github.com/sterlp/eclipse-peon-ai/pull/58
    @Test
    void readDiskFile_lineNumbersStartAtOne() throws IOException {
        Files.writeString(tempDir.resolve("lines.txt"), "alpha\nbeta\ngamma\ndelta\nepsilon");
        // R9: full file - line numbers must start at 1
        String result = tool.diskReadFile("lines.txt", 0, 0);
        assertEquals("   1: alpha\n   2: beta\n   3: gamma\n   4: delta\n   5: epsilon\n",
                result);
    }

    @Test
    void readDiskFile_fromToPreservesActualLineNumbers() throws IOException {
        Files.writeString(tempDir.resolve("lines.txt"), "alpha\nbeta\ngamma\ndelta\nepsilon");
        // reading lines 3-5 must show line numbers 3, 4, 5 - not reset to 1
        String result = tool.diskReadFile("lines.txt", 3, 5);
        assertEquals(
                "   3: gamma\n" +
                "   4: delta\n" +
                "   5: epsilon\n",
                result);
    }

    @Test
    void readDiskFile_fromToMiddleRange() throws IOException {
        Files.writeString(tempDir.resolve("lines.txt"), "alpha\nbeta\ngamma\ndelta\nepsilon");
        // reading lines 2-3 must show line numbers 2 and 3
        String result = tool.diskReadFile("lines.txt", 2, 3);
        assertEquals(
                "   2: beta\n" +
                "   3: gamma\n",
                result);
    }

    @Test
    void readDiskFile_endLineBeyondEndIsClamped() throws IOException {
        Files.writeString(tempDir.resolve("lines.txt"), "alpha\nbeta\ngamma");

        String result = tool.diskReadFile("lines.txt", 2, 99);

        assertEquals("   2: beta\n   3: gamma\n", result);
    }

    @Test
    void readDiskFile_startBeyondEndReturnsHint() throws IOException {
        Files.writeString(tempDir.resolve("lines.txt"), "alpha\nbeta\ngamma");

        String result = tool.diskReadFile("lines.txt", 99, 0);

        assertEquals("file has 3 lines, requested start 99", result);
    }
    
    @Test
    void testUtf8() throws IOException, URISyntaxException {
        // GIVEN
        Path resource = Path.of(getClass().getClassLoader().getResource("utf-8-test.txt").toURI());

        // WHEN
        var result = tool.diskReadFile(resource.normalize().toString(), null, null);

        // THEN
        assertThat(result).isEqualTo("   1: äüß Ö ⚡\n");
    }

    @Test
    void readOnToolLineStartsWithScrollSymbol() throws IOException {
        // GIVEN
        Files.writeString(tempDir.resolve("hello.txt"), "world");
        var lines = new ArrayList<String>();
        tool.withToolRequest(request(monitorCapturing(lines)));

        // WHEN - full read
        String result = tool.diskReadFile("hello.txt", 0, 0);

        // THEN - the done line starts with the symbol, the return stays untouched
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).startsWith("📄 Reading");
        assertThat(lines.get(0)).contains("file hello.txt");
        assertThat(lines.get(0)).endsWith(StringUtil.charsSuffix(result));

        // WHEN - range read
        tool.diskReadFile("hello.txt", 1, 1);

        // THEN
        assertThat(lines).hasSize(2);
        assertThat(lines.get(1)).startsWith("📄 Reading  from 1 to 1 file hello.txt");
    }

    /** Full request wiring (memory + chatModel are required fields) — the model is never called by this tool. */
    private static ToolLoopRequest request(AiMonitor monitor) {
        var config = LlmConfig.builder().model("test").build();
        var cm = new StreamMock().buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("unused"))
                .build());
        return ToolLoopRequest.builder()
                .memory(new ThreadSafeMemory())
                .chatModel(new ConfiguredChatModel(config, cm))
                .monitor(monitor)
                .build();
    }

    private static AiMonitor monitorCapturing(List<String> lines) {
        return new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public void onTool(String message) { lines.add(message); }
        };
    }

    // UC-RT-2
    @Test
    void missingFileWithoutMatchesHasNoSuggestions() throws IOException {
        Path neighbor = tempDir.resolve("neighbor.txt");
        Files.writeString(neighbor, "hello");

        try {
            tool.diskReadFile("Gibtsnicht.java", 0, 0);
            fail("Missing IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("File not found"));
            assertTrue(e.getMessage().contains("Gibtsnicht.java"));
            assertFalse(e.getMessage().contains("Did you mean:"));
        }

        try {
            tool.diskReadFile("nope/neighbor.txt", 0, 0);
            fail("Missing IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            assertTrue(msg.contains("File not found: nope/neighbor.txt also not in " + tempDir));
            assertTrue(msg.contains("Did you mean:"));
            assertTrue(msg.contains(neighbor.toAbsolutePath().toString()));
            assertFalse(msg.contains("hello"));
        }
    }

    // UC-RT-3
    @Test
    void missingFileSuggestsExactlyThreeDiskPaths() throws IOException {
        Files.createDirectories(tempDir.resolve("d1"));
        Files.createDirectories(tempDir.resolve("d2"));
        Files.createDirectories(tempDir.resolve("d3"));
        Files.createDirectories(tempDir.resolve("d4"));
        Path p1 = tempDir.resolve("d1/index.md");
        Path p2 = tempDir.resolve("d2/index.md");
        Path p3 = tempDir.resolve("d3/index.md");
        Path p4 = tempDir.resolve("d4/index.md");
        Files.writeString(p1, "sentinel-one");
        Files.writeString(p2, "sentinel-two");
        Files.writeString(p3, "sentinel-three");
        Files.writeString(p4, "sentinel-four");

        try {
            tool.diskReadFile("nope/index.md", 0, 0);
            fail("Missing IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            assertTrue(msg.contains("File not found: nope/index.md also not in " + tempDir));
            assertTrue(msg.contains("Did you mean:"));
            assertTrue(msg.contains("more matches — use diskSearchFiles"));

            var lines = msg.lines().toList();
            int didYouMeanIdx = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains("Did you mean:")) { didYouMeanIdx = i; break; }
            }
            assertTrue(didYouMeanIdx >= 0);
            var suggestionLines = new ArrayList<String>();
            for (int i = didYouMeanIdx + 1; i < lines.size(); i++) {
                String l = lines.get(i);
                if (l.contains("more matches — use")) break;
                suggestionLines.add(l);
            }
            assertEquals(3, suggestionLines.size());
            var fixturePaths = Arrays.asList(
                    p1.toAbsolutePath().toString(),
                    p2.toAbsolutePath().toString(),
                    p3.toAbsolutePath().toString(),
                    p4.toAbsolutePath().toString());
            for (String sl : suggestionLines) {
                assertTrue(fixturePaths.contains(sl), "unexpected path: " + sl);
            }
            // at most 3 of 4 fixtures shown; which one is excluded is non-deterministic
            assertFalse(msg.contains("sentinel"));
        }
    }

    @Test
    void missingFileUsesLastSegmentAndNeverReadsSuggestion() throws IOException {
        Files.createDirectories(tempDir.resolve("sub"));
        Path target = tempDir.resolve("sub/target.txt");
        Files.writeString(target, "TOP SECRET");

        try {
            tool.diskReadFile("wrong/sub/target.txt", 0, 0);
            fail("Missing IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            assertTrue(msg.contains("wrong/sub/target.txt"));
            assertTrue(msg.contains("Did you mean:"));
            assertTrue(msg.contains(target.toAbsolutePath().toString()));
            assertFalse(msg.contains("TOP SECRET"));
        }

        try {
            tool.diskReadFile(tempDir.resolve("WRONG/target.txt").toAbsolutePath().toString(), 0, 0);
            fail("Missing IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            assertTrue(msg.contains("Did you mean:"));
            assertTrue(msg.contains(target.toAbsolutePath().toString()));
        }

        try {
            tool.diskReadFile("WRONG\\target.txt", 0, 0);
            fail("Missing IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            assertTrue(msg.contains("Did you mean:"));
            assertTrue(msg.contains(target.toAbsolutePath().toString()));
        }
    }

    @Test
    void blankLastSegmentSkipsSuggestionSearch() throws IOException {
        Files.writeString(tempDir.resolve("real.txt"), "content");
        var callCount = new AtomicInteger(0);
        var lastQuery = new String[1];
        var lastLimit = new int[1];
        var countTool = new DiskFileReadTool(tempDir) {
            @Override
            protected List<String> searchFiles(String query, int limit) {
                callCount.incrementAndGet();
                lastQuery[0] = query;
                lastLimit[0] = limit;
                return super.searchFiles(query, limit);
            }
        };

        try {
            countTool.diskReadFile("x/", 0, 0);
            fail("Missing IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("File not found: x/"));
            assertFalse(e.getMessage().contains("Did you mean:"));
            assertEquals(0, callCount.get());
        }

        try {
            countTool.diskReadFile("/", 0, 0);
            fail("Missing IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("File not found: /"));
            assertFalse(e.getMessage().contains("Did you mean:"));
            assertEquals(0, callCount.get());
        }

        try {
            countTool.diskReadFile("x/   ", 0, 0);
            fail("Missing IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("File not found: x/   "));
            assertFalse(e.getMessage().contains("Did you mean:"));
            assertEquals(0, callCount.get());
        }

        try {
            countTool.diskReadFile("nope/real.txt", 0, 0);
            fail("Missing IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("nope/real.txt"));
            assertTrue(e.getMessage().contains("Did you mean:"));
            assertEquals(1, callCount.get());
            assertEquals("real.txt", lastQuery[0]);
            assertEquals(4, lastLimit[0]);
        }
    }

    @Test
    void missingFileKeepsSearchMatchingSemantics() throws IOException {
        Files.writeString(tempDir.resolve("MyFile.java"), "");
        Files.writeString(tempDir.resolve("another_myfile.txt"), "");
        Files.writeString(tempDir.resolve("Other.txt"), "");

        try {
            tool.diskReadFile("nope/*File*.java", 0, 0);
            fail("Missing IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            assertTrue(msg.contains("Did you mean:"));
            assertTrue(msg.contains("MyFile.java"));
            assertFalse(msg.contains("another_myfile.txt"));
        }

        try {
            tool.diskReadFile("x/?nother_myfile.txt", 0, 0);
            fail("Missing IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            assertTrue(msg.contains("Did you mean:"));
            assertTrue(msg.contains("another_myfile.txt"));
        }

        try {
            tool.diskReadFile("x/myfile", 0, 0);
            fail("Missing IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            assertTrue(msg.contains("Did you mean:"));
            assertTrue(msg.contains("another_myfile.txt"));
        }
    }

    @Test
    void missingFileReportsSuggestionSearchFailure() throws IOException {
        tool.setWorkingDir(tempDir.resolve("nonexistent"));

        try {
            tool.diskReadFile("missing.txt", 0, 0);
            fail("Missing IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            assertTrue(msg.contains("File not found: missing.txt"));
            assertTrue(msg.contains("Did you mean: suggestion search failed: Failed to search in"));
            Throwable cause = e.getCause();
            assertNotNull(cause);
            assertTrue(cause instanceof RuntimeException, "expected RuntimeException, got " + cause.getClass());
            assertTrue(cause.getMessage().contains("Failed to search in"));
            Throwable ioCause = cause.getCause();
            assertNotNull(ioCause);
            assertTrue(ioCause instanceof IOException);
        }

        try {
            tool.diskSearchFiles("*.txt", null);
            fail("Missing RuntimeException");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Failed to search in"));
            assertNotNull(e.getCause());
            assertTrue(e.getCause() instanceof IOException);
        }
    }

    @Test
    void suggestionSearchCancellationPropagatesUnchanged() throws IOException {
        Files.writeString(tempDir.resolve("x.java"), "");
        var directCe = new CancellationException("direct-cancel");

        var cancelTool = new DiskFileReadTool(tempDir) {
            @Override
            protected List<String> searchFiles(String query, int limit) {
                throw directCe;
            }
        };

        try {
            cancelTool.diskReadFile("nope/x.java", 0, 0);
            fail("Missing CancellationException");
        } catch (CancellationException e) {
            assertSame(directCe, e);
        }

        var nestedCe = new CancellationException("nested-cancel");
        var wrapped = new RuntimeException("wrap", nestedCe);
        var wrapTool = new DiskFileReadTool(tempDir) {
            @Override
            protected List<String> searchFiles(String query, int limit) {
                throw wrapped;
            }
        };

        try {
            wrapTool.diskReadFile("nope/x.java", 0, 0);
            fail("Missing RuntimeException");
        } catch (RuntimeException e) {
            assertSame(wrapped, e);
            assertTrue(e.getCause() instanceof CancellationException);
            assertSame(nestedCe, e.getCause());
        }
    }
}