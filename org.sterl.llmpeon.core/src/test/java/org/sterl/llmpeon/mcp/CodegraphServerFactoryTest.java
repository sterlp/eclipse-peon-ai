package org.sterl.llmpeon.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.mcp.McpServerConfig.McpTransportType;

/**
 * R-CG-6 / R-CG-10: the built-in server config is STDIO with the project as working
 * directory and NO env vars — in particular no CODEGRAPH_TELEMETRY and no
 * CODEGRAPH_MCP_TOOLS override (server default toolset = only codegraph_explore).
 */
class CodegraphServerFactoryTest {

    @Test
    void givenBinaryAndProject_whenCreate_thenStdioConfigWithWorkDirAndNoEnv() {
        // GIVEN a binary and the project's disk path
        var binary = Path.of("/home/user/.local/bin/codegraph");
        var project = Path.of("/work/peon-ai");

        // WHEN the server config is created
        var config = CodegraphServerFactory.create(binary, project);

        // THEN name/type/command/args/workDir are pinned and envVars stay empty
        assertThat(config.name()).isEqualTo("codegraph");
        assertThat(config.type()).isEqualTo(McpTransportType.STDIO);
        assertThat(config.command()).isEqualTo(binary.toString());
        assertThat(config.args()).isEqualTo("serve --mcp");
        assertThat(config.workingDirectory()).isEqualTo(project.toString());
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
}
