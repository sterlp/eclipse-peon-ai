package org.sterl.llmpeon.docslinter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.tool.ToolService;

class DocsIdToolTest {

    @TempDir
    Path rootDir;

    @Test
    void exposesOnlyNextIds() {
        var core = new DocsLinterTool(rootDir);
        var tool = new DocsIdTool(core);
        var toolService = new ToolService(false);
        toolService.addTool(tool);

        var names = toolService.toolSpecifications().stream()
                .map(s -> s.name())
                .toList();

        assertThat(names).containsExactly("nextIds");
    }

    @Test
    void delegatesToCoreAndReturnsRealAllocation() throws IOException {
        Path docsDir = rootDir.resolve("docs");
        Files.createDirectories(docsDir);
        Files.writeString(docsDir.resolve("a.md"), """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## UC-DL-1 Covered UC ✅
                """);

        var core = new DocsLinterTool(rootDir);
        var tool = new DocsIdTool(core);

        String output = tool.nextIds(null, List.of("docs"), "DL");

        assertThat(output).contains("DL: occupied, next: R-DL-2, UC-DL-2");
    }

    // UC-DL-57
    @Test
    void reportsDiskSourceAndDiscoveryCountWhenAllocationCountIsUnchanged() throws IOException {
        Path docsDir = rootDir.resolve("docs");
        Files.createDirectories(docsDir);

        var core = new DocsLinterTool(rootDir);
        var tool = new DocsIdTool(core);

        String emptyOutput = tool.nextIds(null, List.of("docs"), null);

        Files.writeString(docsDir.resolve("b.md"), """
                # No idPrefix doc
                """);

        String oneSkippedOutput = tool.nextIds(null, List.of("docs"), null);

        assertThat(emptyOutput)
                .startsWith("Disk read — unsaved editor changes are not included.\n\n"
                        + "Scanned: 0 doc file(s): 0 linted, 0 not participating")
                .doesNotContain("occupied")
                .doesNotContain("free");

        assertThat(oneSkippedOutput)
                .startsWith("Disk read — unsaved editor changes are not included.\n\n"
                        + "Scanned: 1 doc file(s): 0 linted, 1 not participating")
                .doesNotContain("occupied")
                .doesNotContain("free");

        assertThat(emptyOutput).isNotEqualTo(oneSkippedOutput);
    }
}
