package org.sterl.llmpeon.docslinter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

/** R-DL-29: start and end messages of the docs linter tools, with the resolved root and duration. */
@Timeout(value = 1)
class DocsLinterLifecycleTest {

    static final String DOC = """
            ---
            idPrefix: XX
            ---
            ### R-XX-1 Rule ✅
            #### UC-XX-1 One ✅
            #### UC-XX-1 Again ✅
            """;

    @TempDir
    Path tmp;

    // UC-DL-103
    @Test
    void startReportsResolvedRootBeforeDiscovery() throws IOException {
        Path repo = tmp.resolve("repo").toAbsolutePath().normalize();
        Files.createDirectories(repo);
        var tool = new DocsLinterTool(tmp.resolve("elsewhere"), clock(new int[] { 0 }));
        var service = new ToolService(false);
        service.addTool(tool);

        var monitor = new CapturingMonitor();
        monitor.onFirst = () -> writeDoc(repo.resolve("docs/a.md"));

        String report = execute(service, "lintDocsAndTests", "{\"root\":\"" + repo + "\"}", monitor);

        assertThat(monitor.tools.getFirst()).isEqualTo("lintDocsAndTests: starting (root: " + repo + ")");
        assertThat(report).isEqualTo("""
                Disk read — unsaved editor changes are not included.
                Doc roots: docs (auto-discovered)

                Scanned: 1 doc file(s): 1 linted, 0 not participating; 0 test source file(s), 0 carrying UC ids
                Root: %s
                Duration: 37 ms
                Sources: docs, .
                UC definitions: 2 / 1; test IDs: 0 / 0; findings: 3

                  UNBELEGT_ERLEDIGT UC-XX-1 docs/a.md:5
                  UNBELEGT_ERLEDIGT UC-XX-1 docs/a.md:6
                  DOPPELT_DEFINIERT UC-XX-1 docs/a.md:5""".formatted(repo));
    }

    private static final String LINT = """
            Disk read — unsaved editor changes are not included.
            Doc roots: docs (explicit)

            Scanned: 1 doc file(s): 1 linted, 0 not participating; tests: not read
            Root: %s
            Duration: 37 ms
            Sources: docs
            UC definitions: 2 / 1; findings: 1

              DOPPELT_DEFINIERT UC-XX-1 docs/a.md:5""";

    private static final String LINT_TESTS = """
            Disk read — unsaved editor changes are not included.
            Doc roots: docs (explicit)

            Scanned: 1 doc file(s): 1 linted, 0 not participating; 1 test source file(s), 1 carrying UC ids
            Root: %s
            Duration: 37 ms
            Sources: docs, .
            UC definitions: 2 / 1; test IDs: 1 / 1; findings: 1

              DOPPELT_DEFINIERT UC-XX-1 docs/a.md:5""";

    private static final String NEXT_IDS = """
            Disk read — unsaved editor changes are not included.
            Doc roots: docs (explicit)

            Scanned: 1 doc file(s): 1 linted, 0 not participating
            Root: %s
            Duration: 37 ms
            XX: occupied, next: R-XX-2, UC-XX-2 (highest found R-XX-1 in docs/a.md)""";

    private Path fixture() throws IOException {
        Path repo = tmp.resolve("repo").toAbsolutePath().normalize();
        writeDoc(repo.resolve("docs/a.md"));
        Files.createDirectories(repo.resolve("src"));
        Files.writeString(repo.resolve("src/a.test.ts"), "// UC-XX-1\n");
        return repo;
    }

    private static LongSupplier clock(int[] reads) {
        long[] values = { 0L, 37_999_999L };
        return () -> values[Math.min(reads[0]++, 1)];
    }

    // UC-DL-104
    // UC-DL-105
    @ParameterizedTest
    @ValueSource(strings = { "lintDocs", "lintDocsAndTests", "nextIds" })
    void endAndReportPinExactElapsedMillis(String name) throws IOException {
        Path repo = fixture();
        int[] reads = { 0 };
        var core = new DocsLinterTool(tmp.resolve("elsewhere"), clock(reads));
        var service = new ToolService(false);
        service.addTool(core);
        service.addTool(new DocsIdTool(core));
        var monitor = new CapturingMonitor();
        String args = "{\"root\":\"" + repo + "\",\"docRoots\":[\"docs\"]"
                + ("nextIds".equals(name) ? ",\"prefix\":\"XX\"" : "") + "}";

        String report = execute(service, name, args, monitor);

        String expected = String.format(switch (name) {
            case "lintDocs" -> LINT;
            case "lintDocsAndTests" -> LINT_TESTS;
            default -> NEXT_IDS;
        }, repo);
        String end = "nextIds".equals(name) ? name + ": 1 docs, 37 ms "
                : name + ": 1 docs, 1 findings, 37 ms ";
        assertThat(report).isEqualTo(expected);
        assertThat(monitor.tools).containsExactly(
                name + ": starting (root: " + repo + ")",
                end + "(" + expected.length() + " chars)");
        assertThat(reads[0]).isEqualTo(2);
    }

    @Test
    void validationAfterStartDoesNotEmitCompletion() throws IOException {
        Path repo = fixture();
        int[] reads = { 0 };
        var tool = new DocsLinterTool(tmp.resolve("elsewhere"), clock(reads));
        var monitor = new CapturingMonitor();
        tool.withToolRequest(request(monitor));

        assertThatThrownBy(() -> tool.lintDocsAndTests(repo.toString(), List.of("docs"),
                List.of("../escape"), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Child root escapes root: ../escape");

        assertThat(monitor.tools).containsExactly("lintDocsAndTests: starting (root: " + repo + ")");
        assertThat(reads[0]).isEqualTo(1);
    }

    // UC-DL-103
    // UC-DL-105
    @Test
    void nextIdsUsesFacadeMonitorWithoutMutatingCoreMonitor() throws IOException {
        Path repo = fixture();
        int[] reads = { 0 };
        var core = new DocsLinterTool(tmp.resolve("elsewhere"), clock(reads));
        var monitorA = new CapturingMonitor();
        core.withToolRequest(request(monitorA));
        var service = new ToolService(false);
        service.addTool(new DocsIdTool(core));
        var monitorB = new CapturingMonitor();

        String report = execute(service, "nextIds",
                "{\"root\":\"" + repo + "\",\"docRoots\":[\"docs\"],\"prefix\":\"XX\"}", monitorB);

        String expected = String.format(NEXT_IDS, repo);
        assertThat(report).isEqualTo(expected);
        assertThat(monitorB.tools).containsExactly(
                "nextIds: starting (root: " + repo + ")",
                "nextIds: 1 docs, 37 ms (" + expected.length() + " chars)");
        assertThat(monitorA.tools).isEmpty();
    }

    private static ToolLoopRequest request(AiMonitor monitor) {
        var model = LlmConfig.newConfig(AiProvider.OLLAMA, "test-model", "http://localhost:9999").build();
        return ToolLoopRequest.builder()
                .memory(new ThreadSafeMemory()).chatModel(model).monitor(monitor).build();
    }

    static String execute(ToolService service, String name, String args, AiMonitor monitor) {
        var request = request(monitor);
        var tr = ToolExecutionRequest.builder().id("1").name(name).arguments(args).build();
        return service.execute(tr, request).text();
    }

    static void writeDoc(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, DOC);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static final class CapturingMonitor implements AiMonitor {
        final List<String> tools = new ArrayList<>();
        Runnable onFirst;

        @Override
        public void onChatResponse(SimpleMessage m) {
            if (m.role() != SimpleMessage.Type.TOOL) return;
            tools.add(m.message());
            if (tools.size() == 1 && onFirst != null) onFirst.run();
        }
    }
}
