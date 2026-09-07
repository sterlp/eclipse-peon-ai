package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.model.SimpleMessage;
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

    // ------------------------------------------------------------------ E5: success messages carry absolute paths

    private ToolLoopRequest requestWith(AiMonitor monitor) {
        var model = LlmConfig.newConfig(AiProvider.OLLAMA, "test-model", "http://localhost:9999").build();
        return ToolLoopRequest.builder()
                .memory(new ThreadSafeMemory())
                .chatModel(model)
                .monitor(monitor)
                .build();
    }

    private void runTool(ToolService ts, String name, String args, ToolLoopRequest req) {
        var tr = ToolExecutionRequest.builder().id("1").name(name).arguments(args).build();
        ts.execute(tr, req);
    }

    /** Captures TOOL chat messages so void tools' success messages can be asserted. */
    private static final class CapturingMonitor implements AiMonitor {
        final List<String> toolMessages = new ArrayList<>();
        @Override
        public void onChatResponse(SimpleMessage m) {
            if (m.role() == SimpleMessage.Type.TOOL) toolMessages.add(m.message());
        }
    }

    @Test
    void writeMessageCarriesAbsolutePath() {
        var ts = new ToolService(false);
        ts.addTool(tool);
        var monitor = new CapturingMonitor();
        var req = requestWith(monitor);
        var abs = tempDir.resolve("sub/x.txt");

        // GIVEN a configured workingDir WHEN diskWriteFile succeeds THEN the Created message carries the absolute path
        runTool(ts, "diskWriteFile", "{\"filePath\":\"sub/x.txt\",\"content\":\"c\"}", req);
        assertThat(monitor.toolMessages).contains("Created file: " + abs);

        // WHEN the same file is written again THEN the Updated message carries the absolute path
        runTool(ts, "diskWriteFile", "{\"filePath\":\"sub/x.txt\",\"content\":\"c2\"}", req);
        assertThat(monitor.toolMessages).contains("Updated file: " + abs);
    }

    @Test
    void deleteMessageCarriesAbsolutePath() throws IOException {
        Files.writeString(tempDir.resolve("gone.txt"), "x");
        var ts = new ToolService(false);
        ts.addTool(tool);
        var monitor = new CapturingMonitor();
        var req = requestWith(monitor);
        var abs = tempDir.resolve("gone.txt");

        // GIVEN workingDir tempDir WHEN diskDeleteFile succeeds THEN the message carries the absolute path
        runTool(ts, "diskDeleteFile", "{\"filePath\":\"gone.txt\"}", req);
        assertThat(monitor.toolMessages).contains("Deleted: " + abs);
    }

    @Test
    void editResultCarriesAbsolutePath() throws IOException {
        Files.writeString(tempDir.resolve("edit.txt"), "x\nx");
        var ts = new ToolService(false);
        ts.addTool(tool);
        var req = requestWith(new CapturingMonitor());
        var abs = tempDir.resolve("edit.txt");

        // GIVEN workingDir tempDir WHEN diskEditFile succeeds THEN the (String) result carries the absolute path
        var tr = ToolExecutionRequest.builder()
                .id("1").name("diskEditFile")
                .arguments("{\"filePath\":\"edit.txt\",\"oldString\":\"x\",\"newString\":\"y\"}")
                .build();
        var result = ts.execute(tr, req);
        assertThat(result.text()).contains("replaced 2 occurrence(s) in " + abs);
    }

    @Test
    void renameResultCarriesAbsolutePaths() throws IOException {
        Files.writeString(tempDir.resolve("orig.txt"), "data");
        var ts = new ToolService(false);
        ts.addTool(tool);
        var src = tempDir.resolve("orig.txt");
        var dst = tempDir.resolve("moved/renamed.txt");

        // GIVEN workingDir tempDir WHEN diskRenameResource succeeds THEN the LLM-visible result carries both absolute paths (R6)
        var result = ts.execute(ToolExecutionRequest.builder().id("1").name("diskRenameResource")
                .arguments("{\"sourcePath\":\"" + src + "\",\"targetPath\":\"" + dst + "\"}").build(),
                requestWith(new CapturingMonitor()));
        assertThat(result.text()).contains("Renamed " + src + " -> " + dst);
    }

    // ------------------------------------------------------------------ Copy tool (file-copy-tool.md R1-R4)

    @Test
    void copyCreatesTargetAndKeepsSource() throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "data");
        var ts = new ToolService(false);
        ts.addTool(tool);
        var src = tempDir.resolve("a.txt");
        var dst = tempDir.resolve("b.txt");

        // GIVEN existing file a.txt WHEN diskCopyFile THEN a copy exists, original kept,
        // R6 LLM-visible result "Copied <s> -> <t>" (would be the "Success" literal before)
        var result = ts.execute(ToolExecutionRequest.builder().id("1").name("diskCopyFile")
                .arguments("{\"sourcePath\":\"" + src + "\",\"targetPath\":\"" + dst + "\"}").build(),
                requestWith(new CapturingMonitor()));
        assertTrue(Files.exists(src));
        assertEquals("data", Files.readString(dst));
        assertThat(result.text()).contains("Copied " + src + " -> " + dst);
    }

    @Test
    void copyCreatesParentDirectories() throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "data");
        var ts = new ToolService(false);
        ts.addTool(tool);

        // GIVEN existing file WHEN copy into a nested path THEN parent dirs are created (R1, like rename)
        runTool(ts, "diskCopyFile",
                "{\"sourcePath\":\"" + tempDir.resolve("a.txt") + "\",\"targetPath\":\"" + tempDir.resolve("sub/b.txt") + "\"}",
                requestWith(new CapturingMonitor()));
        assertEquals("data", Files.readString(tempDir.resolve("sub/b.txt")));
        assertTrue(Files.exists(tempDir.resolve("a.txt")));
    }

    @Test
    void copyFailsWhenTargetExists() throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "data");
        Files.writeString(tempDir.resolve("b.txt"), "existing");

        // GIVEN target already exists WHEN copy THEN error, source + target unchanged (R3 no overwrite)
        assertThrows(IllegalArgumentException.class,
                () -> tool.diskCopyFile(tempDir.resolve("a.txt").toString(), tempDir.resolve("b.txt").toString()));
        assertEquals("data", Files.readString(tempDir.resolve("a.txt")));
        assertEquals("existing", Files.readString(tempDir.resolve("b.txt")));
    }

    @Test
    void copyFailsWhenSourceMissing() {
        // GIVEN no source file WHEN copy THEN "Not found" error
        var ex = assertThrows(IllegalArgumentException.class,
                () -> tool.diskCopyFile(tempDir.resolve("nope.txt").toString(), tempDir.resolve("out.txt").toString()));
        assertTrue(ex.getMessage().contains("Not found"));
    }

    @Test
    void copyFailsWhenSourceIsDirectory() throws IOException {
        Files.createDirectories(tempDir.resolve("somedir"));

        // GIVEN source is a directory WHEN copy THEN "Not a file" error (no recursive dir copy in MVP)
        var ex = assertThrows(IllegalArgumentException.class,
                () -> tool.diskCopyFile(tempDir.resolve("somedir").toString(), tempDir.resolve("out.txt").toString()));
        assertTrue(ex.getMessage().contains("Not a file"));
    }

    // ------------------------------------------------------------------ R5: fully qualified paths only

    @Test
    void copyRejectsRelativeSourcePath() throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "data");

        // GIVEN a relative source path WHEN diskCopyFile THEN contract error, no operation
        var ex = assertThrows(IllegalArgumentException.class,
                () -> tool.diskCopyFile("a.txt", tempDir.resolve("b.txt").toString()));
        assertThat(ex.getMessage()).contains("must be fully qualified").contains("(got: a.txt)");
        assertFalse(Files.exists(tempDir.resolve("b.txt")));
    }

    @Test
    void copyRejectsRelativeTargetPath() throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "data");

        // GIVEN a relative target path WHEN diskCopyFile THEN contract error, no operation
        var ex = assertThrows(IllegalArgumentException.class,
                () -> tool.diskCopyFile(tempDir.resolve("a.txt").toString(), "b.txt"));
        assertThat(ex.getMessage()).contains("must be fully qualified").contains("(got: b.txt)");
        assertFalse(Files.exists(tempDir.resolve("b.txt")));
    }

    @Test
    void renameRejectsRelativeSourcePath() throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "data");

        // GIVEN a relative source path WHEN diskRenameResource THEN contract error, source untouched
        var ex = assertThrows(IllegalArgumentException.class,
                () -> tool.diskRenameResource("a.txt", tempDir.resolve("b.txt").toString()));
        assertThat(ex.getMessage()).contains("must be fully qualified").contains("(got: a.txt)");
        assertTrue(Files.exists(tempDir.resolve("a.txt")));
    }

    @Test
    void renameRejectsRelativeTargetPath() throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "data");

        // GIVEN a relative target path WHEN diskRenameResource THEN contract error, source untouched
        var ex = assertThrows(IllegalArgumentException.class,
                () -> tool.diskRenameResource(tempDir.resolve("a.txt").toString(), "b.txt"));
        assertThat(ex.getMessage()).contains("must be fully qualified").contains("(got: b.txt)");
        assertTrue(Files.exists(tempDir.resolve("a.txt")));
    }
}
