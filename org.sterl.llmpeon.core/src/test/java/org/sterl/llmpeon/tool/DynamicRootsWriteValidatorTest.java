package org.sterl.llmpeon.tool;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.createSymbolicLink;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.list;
import static java.nio.file.Files.readAllBytes;
import static java.nio.file.Files.readString;
import static java.nio.file.Files.writeString;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;
import org.sterl.llmpeon.tool.tools.DiskFileWriteTool;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

class DynamicRootsWriteValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    // UC-WPV-1
    @Test
    @Timeout(2)
    void writeThroughSymlinkOutsideRootRejected() throws Exception {
        var project = createDirectories(tempDir.resolve("projekt"));
        var outside = createDirectories(tempDir.resolve("draussen"));
        var secretFile = outside.resolve("secret.txt");
        writeString(secretFile, "alt");
        var secretBytes = readAllBytes(secretFile);

        var dirLink = project.resolve("link");
        createSymbolicLink(dirLink, outside);
        var fileLink = project.resolve("datei-link");
        createSymbolicLink(fileLink, secretFile);

        var outsideContents = contentsOf(outside);

        var executor = buildExecutor(project);
        var problems = new ArrayList<String>();
        var fileUpdates = new ArrayList<String>();
        var monitor = new AiMonitor() {
            @Override public void onChatResponse(org.sterl.llmpeon.tool.model.SimpleMessage m) {}
            @Override public void onProblem(String message) { problems.add(message); }
            @Override public void onFileUpdate(AiFileUpdate update) { fileUpdates.add(update.file()); }
        };
        var request = buildRequest(project, monitor);

        record Case(String filePath, String content, String originalPathInReturn) {}
        var cases = new Case[] {
            new Case(dirLink.resolve("secret.txt").toString(), "neu", dirLink.resolve("secret.txt").toString()),
            new Case(dirLink.resolve("neu.txt").toString(), "inhalt", dirLink.resolve("neu.txt").toString()),
            new Case(fileLink.toString(), "ersatz", fileLink.toString()),
        };
        for (var c : cases) {
            var result = execute(executor, request, c.filePath, c.content);
            assertThat(result).startsWith("Write denied");
            assertThat(result).contains(c.originalPathInReturn);
            assertThat(result).contains("outside this agent's allowed roots");
            assertThat(readAllBytes(secretFile)).isEqualTo(secretBytes);
            assertThat(contentsOf(outside)).isEqualTo(outsideContents);
        }
        assertThat(problems).hasSize(3);
        assertThat(fileUpdates).isEmpty();

        // dangling symlink — fail-closed: toRealPath() fails, no parent fallback
        var danglingTarget = outside.resolve("noch-nicht-da.txt");
        var danglingLink = project.resolve("kaputt");
        createSymbolicLink(danglingLink, danglingTarget);
        var r4 = execute(executor, request, danglingLink.toString(), "nie");
        assertThat(r4).startsWith("Write denied");
        assertThat(r4).contains(danglingLink.toString());
        assertThat(r4).contains("outside this agent's allowed roots");
        assertThat(exists(danglingTarget)).isFalse();
        assertThat(readAllBytes(secretFile)).isEqualTo(secretBytes);
        assertThat(contentsOf(outside)).isEqualTo(outsideContents);
        assertThat(problems).hasSize(4);
        assertThat(fileUpdates).isEmpty();
    }

    // UC-WPV-2
    @Test
    @Timeout(2)
    void writeThroughSymlinkInsideRootAllowed() throws Exception {
        var project = createDirectories(tempDir.resolve("projekt"));
        var src = createDirectories(project.resolve("src"));
        var kurz = project.resolve("kurz");
        createSymbolicLink(kurz, src);

        var rootLink = tempDir.resolve("projekt-link");
        createSymbolicLink(rootLink, project);

        var executor = buildExecutor(rootLink);
        var problems = new ArrayList<String>();
        var monitor = new AiMonitor() {
            @Override public void onChatResponse(org.sterl.llmpeon.tool.model.SimpleMessage m) {}
            @Override public void onProblem(String message) { problems.add(message); }
        };
        var request = buildRequest(rootLink, monitor);

        var r1 = execute(executor, request, "kurz/a.txt", "inhalt-a");
        assertThat(r1).isEqualTo("Success");
        assertThat(readString(src.resolve("a.txt"))).isEqualTo("inhalt-a");

        var r2 = execute(executor, request, "kurz/new/deep/b.txt", "inhalt-b");
        assertThat(r2).isEqualTo("Success");
        assertThat(readString(src.resolve("new/deep/b.txt"))).isEqualTo("inhalt-b");

        assertThat(problems).isEmpty();
    }

    private SmartToolExecutor buildExecutor(Path root) {
        var toolService = new ToolService(false);
        toolService.addTool(new DiskFileWriteTool(root));
        return toolService.getExecutors().stream()
                .filter(e -> e.getSpec().name().equals("diskWriteFile"))
                .findFirst().orElseThrow();
    }

    private ToolLoopRequest buildRequest(Path root, AiMonitor monitor) {
        ConfiguredChatModel chatModel = LlmConfig.newOpenAi("test-model").build();
        var req = ToolLoopRequest.builder()
                .memory(new ThreadSafeMemory())
                .chatModel(chatModel)
                .build();
        req.writeValidator = new DynamicRootsWriteValidator(root, List::of);
        req.monitor = monitor;
        return req;
    }

    private String execute(SmartToolExecutor executor, ToolLoopRequest request, String filePath, String content) throws Exception {
        var args = MAPPER.writeValueAsString(Map.of("filePath", filePath, "content", content));
        var call = ToolExecutionRequest.builder().id("1").name("diskWriteFile").arguments(args).build();
        return executor.run(call, request);
    }

    private static java.util.Set<String> contentsOf(Path dir) throws Exception {
        if (!exists(dir)) return java.util.Set.of();
        try (var stream = list(dir)) {
            return stream.map(p -> p.getFileName().toString()).collect(java.util.stream.Collectors.toSet());
        }
    }
}