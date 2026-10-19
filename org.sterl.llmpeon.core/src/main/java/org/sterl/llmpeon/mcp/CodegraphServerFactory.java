package org.sterl.llmpeon.mcp;

import java.nio.file.Path;

import org.sterl.llmpeon.mcp.McpServerConfig.McpTransportType;

/**
 * Builds the built-in Codegraph server config: STDIO, {@code serve --mcp}, the index
 * directory as working directory. No env vars are set — in particular no
 * {@code CODEGRAPH_TELEMETRY} (R-CG-10) and no {@code CODEGRAPH_MCP_TOOLS} override, so the
 * server default toolset (only {@code codegraph_explore}) applies (R-CG-6).
 *
 * <p>The binary is a hint, not a gate (R-CG-2): a null binary still starts the server —
 * {@code codegraph} via the process PATH, on Windows via {@code cmd /c} (npm installs
 * {@code .cmd} shims that {@code ProcessBuilder} cannot launch directly).
 */
public final class CodegraphServerFactory {

    /** Built-in MCP group / server name — reserved, the built-in wins (R-CG-11). */
    public static final String NAME = "codegraph";
    public static final String ARGS = "serve --mcp";
    /** Windows fallback args: the start goes through the shell so the npm .cmd shim resolves (R-CG-2). */
    public static final String WINDOWS_ARGS = "/c codegraph serve --mcp";

    private CodegraphServerFactory() {
    }

    public static McpServerConfig create(Path binary, Path workDir) {
        return create(binary, workDir, isWindows());
    }

    /** Package-private seam: the platform is injected so both branches are testable. */
    static McpServerConfig create(Path binary, Path workDir, boolean windows) {
        String command;
        String args;
        if (binary != null) {
            command = binary.toString();
            args = ARGS;
        } else if (windows) {
            command = "cmd";
            args = WINDOWS_ARGS;
        } else {
            command = "codegraph";
            args = ARGS;
        }
        return new McpServerConfig(NAME, McpTransportType.STDIO, "", "", "",
                command, args, "", workDir.toString());
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
