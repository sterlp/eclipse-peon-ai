package org.sterl.llmpeon.mcp;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages connections to one or more MCP servers and exposes their tools.
 *
 * <p>Call {@link #connect()} to open connections to all configured servers.
 * Call {@link #disconnect()} or {@link #close()} to release them.
 */
@Slf4j
public class McpService implements AutoCloseable {

    private final List<McpServerConfig> servers;
    private final List<McpClient> clients = new ArrayList<>();
    private final Map<String, McpClient> toolToClient = new HashMap<>();
    private final List<ToolSpecification> toolSpecs = new ArrayList<>();

    public McpService(List<McpServerConfig> servers) {
        this.servers = servers != null ? servers : List.of();
    }

    /**
     * Opens a temporary connection to the given server, lists its tools, then closes.
     * Returns the tool names — throws on any failure.
     */
    public static List<String> testConnect(McpServerConfig server) {
        try (var svc = new McpService(List.of(server))) {
            svc.connect();
            return svc.getToolSpecifications().stream()
                    .map(ToolSpecification::name)
                    .toList();
        } catch (Exception e) {
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException(e);
        }
    }

    /**
     * Opens connections to all configured servers, fetches their tool listings,
     * and populates the internal tool registry.
     * If any server fails to connect, all successfully created clients are closed
     * and a RuntimeException is thrown listing all failures.
     */
    public void connect() {
        disconnect();
        for (var server : servers) {
            try {
                McpTransport transport = buildTransport(server);
                if (transport == null) continue;

                McpClient client = DefaultMcpClient.builder()
                        .transport(transport)
                        .protocolVersion(server.protocolVersion())
                        .key(server.name())
                        .initializationTimeout(Duration.ofSeconds(15))
                        .toolExecutionTimeout(Duration.ofSeconds(60))
                        .build();

                var tools = client.listTools();
                clients.add(client);
                for (var spec : tools) {
                    toolSpecs.add(spec);
                    toolToClient.put(spec.name(), client);
                    log.info("Connected MCP " + server.name() + " tool " + spec.name());
                }
            } catch (Exception e) {
                var location = server.type() == McpServerConfig.McpTransportType.STDIO
                        ? server.command() + " " + server.args()
                        : server.url();
                disconnect();
                throw new RuntimeException("MCP failed to connect to: " + server.name() + " (" + location + "): " + e.getMessage());
            }
        }
    }

    private McpTransport buildTransport(McpServerConfig server) {
        if (server.type() == McpServerConfig.McpTransportType.STDIO) {
            return buildStdioMcp(server);
        } else {
            return buildWebMcp(server);
        }
    }

    private McpTransport buildStdioMcp(McpServerConfig server) {
        if (server.command().isBlank()) {
            throw new IllegalArgumentException(server.name() + ": STDIO server requires a command");
        }
        var cmd = new ArrayList<String>();
        cmd.add(server.command().trim());
        if (!server.args().isBlank()) {
            Arrays.stream(server.args().trim().split("\\s+")).forEach(cmd::add);
        }
        var envMap = parseEnvVars(server.envVars());
        var builder = StdioMcpTransport.builder()
                .command(cmd)
                .logEvents(false);
        if (!envMap.isEmpty()) builder.environment(envMap);
        return builder.build();
    }

    private McpTransport buildWebMcp(McpServerConfig server) {
        if (server.url().isBlank()) return null;
        var tb = StreamableHttpMcpTransport.builder()
                .url(server.url())
                .timeout(Duration.ofSeconds(30))
                .logRequests(false)
                .logResponses(false);
        if (!server.apiKey().isBlank()) {
            tb.customHeaders(Map.of("Authorization", "Bearer " + server.apiKey()));
        }
        return tb.build();
    }

    private static Map<String, String> parseEnvVars(String raw) {
        var map = new LinkedHashMap<String, String>();
        if (raw == null || raw.isBlank()) return map;
        for (var line : raw.split("\\r?\\n")) {
            var eq = line.indexOf('=');
            if (eq > 0) map.put(line.substring(0, eq).trim(), line.substring(eq + 1));
        }
        return map;
    }

    /**
     * Executes an MCP tool by name. Returns an error string if the tool is not found.
     */
    public String executeTool(ToolExecutionRequest request) {
        var client = toolToClient.get(request.name());
        if (client == null) {
            return "Error: MCP tool '" + request.name() + "' not found";
        }
        try {
            var result = client.executeTool(request);
            return result.resultText();
        } catch (Exception e) {
            return "MCP tool error (" + request.name() + "): " + e.getMessage();
        }
    }

    /** Returns the aggregated tool specifications from all connected servers. */
    public List<ToolSpecification> getToolSpecifications() {
        return Collections.unmodifiableList(toolSpecs);
    }

    /** Returns true if this tool name belongs to an MCP tool. */
    public boolean hasTool(String toolName) {
        return toolToClient.containsKey(toolName);
    }

    /** Closes all client connections and clears the tool registry. */
    public void disconnect() {
        for (var client : clients) {
            try {
                client.close();
            } catch (Exception ignored) {}
        }
        clients.clear();
        toolToClient.clear();
        toolSpecs.clear();
    }

    @Override
    public void close() {
        disconnect();
    }
}
