package org.sterl.llmpeon.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.mcp.McpServerConfig.McpTransportType;

/**
 * Wire test for the STDIO transport: {@code workingDirectory} follows "empty means unset" —
 * a set value is wired into the transport, a blank value leaves the transport field null so the
 * subprocess inherits the current working directory (langchain4j StdioMcpTransport semantics).
 */
class McpServiceStdioWireTest {

    @Test
    void givenWorkingDirectorySet_whenBuildStdioMcp_thenTransportCarriesDirectory() {
        // GIVEN a STDIO server with a working directory
        var server = stdioServer("/tmp/codegraph-proj");
        var service = new McpService(List.of(server));

        // WHEN the transport is built
        var transport = service.buildStdioMcp(server);

        // THEN the transport carries the directory (subprocess starts there)
        assertThat(workingDirectoryOf(transport)).isEqualTo(Path.of("/tmp/codegraph-proj"));
    }

    @Test
    void givenBlankWorkingDirectory_whenBuildStdioMcp_thenTransportDirectoryUnset() {
        // GIVEN a STDIO server without a working directory (empty = unset)
        var server = stdioServer("");
        var service = new McpService(List.of(server));

        // WHEN the transport is built
        var transport = service.buildStdioMcp(server);

        // THEN the transport field stays null — the subprocess inherits the cwd (no silent default)
        assertThat(workingDirectoryOf(transport)).isNull();
    }

    private static McpServerConfig stdioServer(String workDir) {
        return new McpServerConfig("cg", McpTransportType.STDIO, "", "", "",
                "codegraph", "serve --mcp", "", workDir);
    }

    private static Path workingDirectoryOf(Object transport) {
        try {
            Field field = transport.getClass().getDeclaredField("workingDirectory");
            field.setAccessible(true);
            return (Path) field.get(transport);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("StdioMcpTransport field 'workingDirectory' not found", e);
        }
    }
}
