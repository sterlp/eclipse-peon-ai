package org.sterl.llmpeon.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.tool.tools.DiskFileWriteTool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;


class DiskFileWriteToolTest {

    @TempDir
    Path tempDir;

    DiskFileWriteTool tool;

    @BeforeEach
    void setUp() {
        tool = new DiskFileWriteTool(tempDir);
    }

    @Test
    void writeDiskFile_newFile() {
        tool.diskWriteFile("sub/dir/test.txt", "content");
        assertTrue(Files.exists(tempDir.resolve("sub/dir/test.txt")));
    }

    @Test
    void writeDiskFile_overwriteExisting() throws IOException {
        Files.writeString(tempDir.resolve("existing.txt"), "old");
        tool.diskWriteFile("existing.txt", "new");
        assertEquals("new", Files.readString(tempDir.resolve("existing.txt")));
    }

    @Test
    void writeDiskFile_existingFile() throws IOException {
        Files.writeString(tempDir.resolve("data.txt"), "before");
        tool.diskWriteFile("data.txt", "after");
        assertEquals("after", Files.readString(tempDir.resolve("data.txt")));
    }

    @Test
    void writeDiskFile_emptyContentAllowed() throws IOException {
        Files.writeString(tempDir.resolve("truncate.txt"), "before");
        tool.diskWriteFile("truncate.txt", "");
        assertEquals("", Files.readString(tempDir.resolve("truncate.txt")));
    }

    @Test
    void deleteDiskFile_existingFile() throws IOException {
        Files.writeString(tempDir.resolve("del.txt"), "bye");
        tool.diskDeleteFile("del.txt");
        assertFalse(Files.exists(tempDir.resolve("del.txt")));
    }

    @Test
    void deleteDiskFile_missingFile() {
        assertThrows(IllegalArgumentException.class, () -> tool.diskDeleteFile("nope.txt"));
    }

    @Test
    void deleteDiskFile_recursiveDirectory() throws IOException {
        Path dir = tempDir.resolve("nested/parent/child");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("file1.txt"), "a");
        Files.writeString(dir.resolve("file2.txt"), "b");
        Files.writeString(tempDir.resolve("nested/parent/file3.txt"), "c");

        tool.diskDeleteFile("nested");
        assertFalse(Files.exists(tempDir.resolve("nested")));
    }

    @Test
    void insertDiskLines_afterLine() throws IOException {
        Files.writeString(tempDir.resolve("ins.txt"), "a\nb\nc");
        tool.diskInsertLines("ins.txt", 2, "x\ny");
        assertEquals("a\nb\nx\ny\nc", Files.readString(tempDir.resolve("ins.txt")));
    }

    @Test
    void insertDiskLines_prepend() throws IOException {
        Files.writeString(tempDir.resolve("ins.txt"), "a\nb");
        tool.diskInsertLines("ins.txt", 0, "x");
        assertEquals("x\na\nb", Files.readString(tempDir.resolve("ins.txt")));
    }

    @Test
    void insertDiskLines_append() throws IOException {
        Files.writeString(tempDir.resolve("ins.txt"), "a\nb");
        tool.diskInsertLines("ins.txt", null, "x");
        assertEquals("a\nb\nx", Files.readString(tempDir.resolve("ins.txt")));
    }

    @Test
    void replaceDiskLines_basic() throws IOException {
        Files.writeString(tempDir.resolve("rep.txt"), "line1\nline2\nline3");
        tool.diskReplaceLines("rep.txt", 2, "replaced");
        assertEquals("line1\nreplaced\nline3", Files.readString(tempDir.resolve("rep.txt")));
    }

    @Test
    void replaceDiskLines_multiLine() throws IOException {
        Files.writeString(tempDir.resolve("rep.txt"), "a\nb\nc\nd");
        tool.diskReplaceLines("rep.txt", 2, "x\ny");
        assertEquals("a\nx\ny\nc\nd", Files.readString(tempDir.resolve("rep.txt")));
    }

    private ToolLoopRequest docsRequest() {
        var model = LlmConfig.newConfig(AiProvider.OLLAMA, "test-model", "http://localhost:9999").build();
        return ToolLoopRequest.builder()
                .memory(new ThreadSafeMemory())
                .chatModel(model)
                .writeValidator(WriteValidator.DOCS)
                .build();
    }

    @Test
    void diskEditFile_reportsReplacementCount() throws IOException {
        Files.writeString(tempDir.resolve("edit.txt"), "x\nx");
        var ts = new ToolService(false);
        ts.addTool(tool);

        var model = LlmConfig.newConfig(AiProvider.OLLAMA, "test-model", "http://localhost:9999").build();
        var req = ToolLoopRequest.builder()
                .memory(new ThreadSafeMemory())
                .chatModel(model)
                .build();

        var tr = ToolExecutionRequest.builder()
                .id("1")
                .name("diskEditFile")
                .arguments("{\"filePath\":\"edit.txt\",\"oldString\":\"x\",\"newString\":\"y\"}")
                .build();

        var result = ts.execute(tr, req);
        assertTrue(result.text().contains("replaced 2 occurrence(s)"),
                "LLM-visible result should report the replacement count, was: " + result.text());
    }

    @Test
    void diskEditFile_nullNewStringReportsDeletedCount() throws IOException {
        Files.writeString(tempDir.resolve("edit.txt"), "x\nx");
        var ts = new ToolService(false);
        ts.addTool(tool);

        var model = LlmConfig.newConfig(AiProvider.OLLAMA, "test-model", "http://localhost:9999").build();
        var req = ToolLoopRequest.builder()
                .memory(new ThreadSafeMemory())
                .chatModel(model)
                .build();

        var tr = ToolExecutionRequest.builder()
                .id("1")
                .name("diskEditFile")
                .arguments("{\"filePath\":\"edit.txt\",\"oldString\":\"x\",\"newString\":null}")
                .build();

        var result = ts.execute(tr, req);
        assertTrue(result.text().contains("deleted 2 occurrence(s)"),
                "LLM-visible result should report the delete count, was: " + result.text());
    }


    @Test
    void write_allowedInsideDocs() {
        tool.withToolRequest(docsRequest());
        tool.diskWriteFile("proj/docs/feature.md", "hello");
        assertTrue(Files.exists(tempDir.resolve("proj/docs/feature.md")));
    }

    @Test
    void write_rejectedOutsideDocs() {
        tool.withToolRequest(docsRequest());
        assertThrows(IllegalArgumentException.class,
                () -> tool.diskWriteFile("src/main/java/Foo.java", "x"));
        assertFalse(Files.exists(tempDir.resolve("src/main/java/Foo.java")));
    }

    @Test
    void write_withoutRequest_isUnrestricted() {
        tool.diskWriteFile("anywhere/file.txt", "x"); // no withToolRequest -> request == null
        assertTrue(Files.exists(tempDir.resolve("anywhere/file.txt")));
    }
}
