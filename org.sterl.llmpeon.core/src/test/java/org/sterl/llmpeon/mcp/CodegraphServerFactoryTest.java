package org.sterl.llmpeon.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.mcp.McpServerConfig.McpTransportType;

/**
 * R-CG-6 / R-CG-10 / R-CG-2: the built-in server config is STDIO with the index directory
 * as working directory and NO env vars — in particular no CODEGRAPH_TELEMETRY and no
 * CODEGRAPH_MCP_TOOLS override (server default toolset = only codegraph_explore). The binary
 * is a hint: a null binary falls back to the PATH command (cmd /c on Windows).
 */
class CodegraphServerFactoryTest {

    @Test
    void givenBinaryAndWorkDir_whenCreate_thenStdioConfigWithWorkDirAndNoEnv() {
        // GIVEN a binary and the index directory
        var binary = Path.of("/home/user/.local/bin/codegraph");
        var workDir = Path.of("/work/peon-ai");

        // WHEN the server config is created
        var config = CodegraphServerFactory.create(binary, workDir);

        // THEN name/type/command/args/workDir are pinned and envVars stay empty
        assertThat(config.name()).isEqualTo("codegraph");
        assertThat(config.type()).isEqualTo(McpTransportType.STDIO);
        assertThat(config.command()).isEqualTo(binary.toString());
        assertThat(config.args()).isEqualTo("serve --mcp");
        assertThat(config.workingDirectory()).isEqualTo(workDir.toString());
        assertThat(config.envVars()).isEmpty();
    }

    @Test
    void givenConstants_whenReferenced_thenReservedNameAndArgs() {
        // GIVEN the factory constants
        // (consumed by the reserved-name filter and the connection service)

        // THEN the built-in owns the name "codegraph" with the serve --mcp args
        assertThat(CodegraphServerFactory.NAME).isEqualTo("codegraph");
        assertThat(CodegraphServerFactory.ARGS).isEqualTo("serve --mcp");
    }

    /** R-CG-2: GIVEN no binary (the hint search is negative) on a non-Windows platform
     *  WHEN the config is created THEN the start is still attempted — via the process PATH. */
    @Test
    void fallsBackToPathCommand() {
        // GIVEN no binary, a non-Windows platform
        var workDir = Path.of("/work/peon-ai");

        // WHEN the server config is created
        var config = CodegraphServerFactory.create(null, workDir, false);

        // THEN the command resolves via the process PATH
        assertThat(config.type()).isEqualTo(McpTransportType.STDIO);
        assertThat(config.command()).isEqualTo("codegraph");
        assertThat(config.args()).isEqualTo("serve --mcp");
        assertThat(config.workingDirectory()).isEqualTo(workDir.toString());
        assertThat(config.envVars()).isEmpty();
    }

    /** R-CG-2: GIVEN no binary on Windows WHEN the config is created THEN the start goes
     *  through {@code cmd /c} — npm .cmd shims cannot be launched by ProcessBuilder directly. */
    @Test
    void windowsUsesCmdShim() {
        // GIVEN no binary, a Windows platform
        var workDir = Path.of("C:/work/peon-ai");

        // WHEN the server config is created
        var config = CodegraphServerFactory.create(null, workDir, true);

        // THEN the shell resolves the .cmd shim
        assertThat(config.command()).isEqualTo("cmd");
        assertThat(config.args()).isEqualTo("/c codegraph serve --mcp");
        assertThat(config.workingDirectory()).isEqualTo(workDir.toString());
    }
}
