package org.sterl.llmpeon.mcp;

/**
 * Configuration for a single MCP server.
 *
 * @param name            display name shown in the UI
 * @param url             SSE endpoint URL, e.g. https://mcp.context7.com/sse
 * @param apiKey          optional Bearer token; empty string means no auth
 * @param protocolVersion MCP protocol version to announce, e.g. "2024-11-05"
 */
public record McpServerConfig(
        String name,
        String url,
        String apiKey,
        String protocolVersion) {

    public static final String DEFAULT_PROTOCOL_VERSION = "2024-11-05";

    public McpServerConfig {
        if (name == null) name = "";
        if (url == null) url = "";
        if (apiKey == null) apiKey = "";
        if (protocolVersion == null || protocolVersion.isBlank()) protocolVersion = DEFAULT_PROTOCOL_VERSION;
    }
}
