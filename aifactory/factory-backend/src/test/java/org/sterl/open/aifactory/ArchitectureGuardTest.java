package org.sterl.open.aifactory;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * PR-4 / AF-ADR-001 as an executable rule: the factory talks to the Core through plain disk paths
 * only — no Eclipse runtime, no plugin composition root. ArchUnit is deliberately not pulled in
 * (offline builds), a source scan is enough for this rule and reports the offending file:line.
 */
class ArchitectureGuardTest {

    /** Import of anything Eclipse. */
    private static final Pattern ECLIPSE_IMPORT = Pattern.compile("^\\s*import\\s+(static\\s+)?org\\.eclipse\\.");

    /** Plugin classes that only work inside the Eclipse plugin — forbidden in factory sources. */
    private static final List<Pattern> FORBIDDEN_PLUGIN_TYPES = List.of(
            type("PeonAiService"), type("SharedToolsComponent"), type("AgentContextComponent"),
            type("BuildPoAgentComponent"), type("Eclipse\\w*"), type("AskUserTool"),
            type("WorkspaceMemoryTool"), type("PlanTool"), type("PlanReadTool"),
            type("StaticContextItem"), type("AgentsMdContextItem"));

    private static Pattern type(String simpleName) {
        return Pattern.compile("\\b" + simpleName + "\\b");
    }

    @Test
    void noEclipseImportsInFactorySources() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : mainSources()) {
            List<String> lines = Files.readAllLines(source);
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                if (ECLIPSE_IMPORT.matcher(line).find()) {
                    violations.add(relative(source) + ":" + (index + 1) + " " + line.trim());
                }
            }
        }
        assertThat(violations)
                .as("factory sources must not import org.eclipse.* (PR-4, AF-ADR-001)")
                .isEmpty();
    }

    @Test
    void noPluginCompositionClassIsUsed() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : mainSources()) {
            List<String> lines = Files.readAllLines(source);
            for (int index = 0; index < lines.size(); index++) {
                Matcher matcher = forbiddenType(lines.get(index));
                if (matcher != null) {
                    violations.add(relative(source) + ":" + (index + 1) + " " + matcher.group());
                }
            }
        }
        assertThat(violations)
                .as("plugin-only classes must never appear in factory sources")
                .isEmpty();
    }

    /** the offending type in this line, or null */
    private static Matcher forbiddenType(String line) {
        for (Pattern forbidden : FORBIDDEN_PLUGIN_TYPES) {
            Matcher matcher = forbidden.matcher(line);
            if (matcher.find()) {
                return matcher;
            }
        }
        return null;
    }

    private static List<Path> mainSources() throws IOException {
        Path root = mainSourceRoot();
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }

    /** The scan runs over {@code src/main/java} of this module — found from the test's own classes dir. */
    private static Path mainSourceRoot() {
        Path classes = Path.of(System.getProperty("user.dir")).resolve("target/classes").normalize();
        Path root = classes.getParent().getParent().resolve("src").resolve("main").resolve("java");
        assertThat(Files.isDirectory(root))
                .as("src/main/java of the module under test: " + root)
                .isTrue();
        return root;
    }

    private static String relative(Path source) {
        return Path.of(System.getProperty("user.dir")).relativize(source).toString();
    }
}
