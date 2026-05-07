package org.sterl.llmpeon.mcp;

import org.sterl.llmpeon.shared.StringUtil;

/**
 * Configuration for a single MCP server — either HTTP/SSE or a local STDIO subprocess.
 *
 * @param name            display name shown in the UI
 * @param type            transport type: HTTP (SSE) or STDIO (local process)
 * @param url             SSE endpoint URL — HTTP only, e.g. https://mcp.context7.com/mcp
 * @param apiKey          optional Bearer token — HTTP only
 * @param protocolVersion MCP protocol version to announce, e.g. "2024-11-05"
 * @param command         executable to launch — STDIO only, e.g. "uvx"
 * @param args            space-separated arguments — STDIO only, e.g. "duckduckgo-mcp-server"
 * @param envVars         KEY=VALUE lines passed as environment variables — STDIO only
 */
public record McpServerConfig(
        String name,
        McpTransportType type,
        String url,
        String apiKey,
        String protocolVersion,
        String command,
        String args,
        String envVars) {

    public enum McpTransportType { HTTP, HTTP_SSE, STDIO }

    public static final String DEFAULT_PROTOCOL_VERSION = "2025-06-18";

    public McpServerConfig {
        if (type == null)   type = McpTransportType.HTTP;
        if (name == null)   name = "";
        if (url == null)    url = "";
        if (apiKey == null) apiKey = "";
        if (command == null) command = "";
        if (args == null)   args = "";
        if (envVars == null) envVars = "";
        if (StringUtil.hasNoValue(protocolVersion)) protocolVersion = DEFAULT_PROTOCOL_VERSION;
    }

    public McpServerConfig(String name, McpTransportType transport, String url) {
        this(name, transport, url, "", DEFAULT_PROTOCOL_VERSION, "", "", "");
    }
}
