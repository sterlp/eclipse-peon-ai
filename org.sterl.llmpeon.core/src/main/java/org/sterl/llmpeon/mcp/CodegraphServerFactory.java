package org.sterl.llmpeon.mcp;

import java.nio.file.Path;

import org.sterl.llmpeon.mcp.McpServerConfig.McpTransportType;

/**
 * Builds the built-in Codegraph server config: STDIO, {@code serve --mcp}, the project's
 * disk path as working directory. No env vars are set — in particular no
 * {@code CODEGRAPH_TELEMETRY} (R-CG-10) and no {@code CODEGRAPH_MCP_TOOLS} override, so the
 * server default toolset (only {@code codegraph_explore}) applies (R-CG-6).
 */
public final class CodegraphServerFactory {

    /** Built-in MCP group / server name — reserved, the built-in wins (R-CG-11). */
    public static final String NAME = "codegraph";
    public static final String ARGS = "serve --mcp";

    private CodegraphServerFactory() {
    }

    public static McpServerConfig create(Path binary, Path projectDiskPath) {
        return new McpServerConfig(NAME, McpTransportType.STDIO, "", "", "",
                binary.toString(), ARGS, "", projectDiskPath.toString());
    }
}
