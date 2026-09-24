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

    // UC-DL-69
    @Test
    void flatRegistryIsDetectedAndContinuedFlat() throws IOException {
        Path docsDir = rootDir.resolve("docs");
        Files.createDirectories(docsDir);
        Files.writeString(docsDir.resolve("offene-punkte.md"), """
                # Offene Punkte

                - OP-70 Erster
                - OP-71 Zweiter
                - OP-72 Dritter
                - OP-73 Vierter
                - OP-74 Fünfter
                - OP-75 Sechster
                - OP-76 Siebter
                - OP-77 Achter
                - OP-78 Neunter
                - OP-79 Zehnter
                """);
        Files.writeString(docsDir.resolve("a.md"), """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done
                """);

        var core = new DocsLinterTool(rootDir);
        var tool = new DocsIdTool(core);

        String output = tool.nextIds(null, List.of("docs"), "OP");

        assertThat(output).contains(
                "OP: occupied, next: OP-80 (highest found OP-79 in docs/offene-punkte.md)");
        assertThat(output).doesNotContain("R-OP-");
        assertThat(output).doesNotContain("free");
    }

    // UC-DL-70
    @Test
    void ruleAndUseCaseFormsUnchanged() throws IOException {
        Path docsDir = rootDir.resolve("docs");
        Files.createDirectories(docsDir);
        Files.writeString(docsDir.resolve("a-high.md"), """
                ---
                idPrefix: READTOOLS
                ---

                # R-READTOOLS-1 High rule ✅ done

                ## UC-READTOOLS-5 Five ✅

                ## UC-READTOOLS-7 Seven ✅
                """);
        Files.writeString(docsDir.resolve("z-low.md"), """
                ---
                idPrefix: READTOOLS
                ---

                # R-READTOOLS-2 Low rule ✅ done

                # R-READTOOLS-3 Mid rule ✅ done

                # R-READTOOLS-4 Last rule ✅ done

                ## UC-READTOOLS-1 One ✅

                ## UC-READTOOLS-2 Two ✅

                ## UC-READTOOLS-3 Three ✅

                ## UC-READTOOLS-4 Four ✅

                ## UC-READTOOLS-6 Six ✅
                """);

        var core = new DocsLinterTool(rootDir);
        var tool = new DocsIdTool(core);

        String output = tool.nextIds(null, List.of("docs"), "READTOOLS");

        assertThat(output).contains("next: R-READTOOLS-5, UC-READTOOLS-8");
    }

    // UC-DL-71
    @Test
    void mentionWithoutDefinitionBurnsNumber() throws IOException {
        Path docsDir = rootDir.resolve("docs");
        Files.createDirectories(docsDir);
        Files.writeString(docsDir.resolve("foreign.md"), """
                # Notes

                Referenced UC-FOO-3 in passing.
                """);
        Files.writeString(docsDir.resolve("a.md"), """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done
                """);

        var core = new DocsLinterTool(rootDir);
        var tool = new DocsIdTool(core);

        String output = tool.nextIds(null, List.of("docs"), "FOO");

        assertThat(output).contains("FOO: occupied, next: R-FOO-1, UC-FOO-4");
        assertThat(output).contains("highest found UC-FOO-3 in docs/foreign.md");
    }

    // UC-DL-72
    @Test
    void freeOnlyWhenNothingFoundAnywhere() throws IOException {
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

        String output = tool.nextIds(null, List.of("docs"), "FOO");

        assertThat(output).contains("FOO: free, would start: R-FOO-1, UC-FOO-1");
    }

    @Test
    void flatOccurrenceBeatsLowerDefinitions() throws IOException {
        Path docsDir = rootDir.resolve("docs");
        Files.createDirectories(docsDir);
        Files.writeString(docsDir.resolve("a.md"), """
                ---
                idPrefix: OP
                ---

                # R-OP-9 Rule ✅ done
                """);
        Files.writeString(docsDir.resolve("offene-punkte.md"), """
                # Offene Punkte

                - OP-79 Flat
                """);

        var core = new DocsLinterTool(rootDir);
        var tool = new DocsIdTool(core);

        String output = tool.nextIds(null, List.of("docs"), "OP");

        assertThat(output).contains(
                "OP: occupied, next: OP-80 (highest found OP-79 in docs/offene-punkte.md)");
    }

    @Test
    void definitionBeatsLowerFlatOccurrence() throws IOException {
        Path docsDir = rootDir.resolve("docs");
        Files.createDirectories(docsDir);
        Files.writeString(docsDir.resolve("a.md"), """
                ---
                idPrefix: OP
                ---

                # R-OP-90 Rule ✅ done
                """);
        Files.writeString(docsDir.resolve("offene-punkte.md"), """
                # Offene Punkte

                - OP-79 Flat
                """);

        var core = new DocsLinterTool(rootDir);
        var tool = new DocsIdTool(core);

        String output = tool.nextIds(null, List.of("docs"), "OP");

        assertThat(output).contains("next: R-OP-91, UC-OP-1");
        assertThat(output).contains("highest found R-OP-90 in docs/a.md");
    }
}
