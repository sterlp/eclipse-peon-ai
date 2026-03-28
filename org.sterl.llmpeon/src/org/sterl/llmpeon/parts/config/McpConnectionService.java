package org.sterl.llmpeon.parts.config;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.eclipse.core.runtime.jobs.Job;
import org.sterl.llmpeon.mcp.McpServerConfig;
import org.sterl.llmpeon.parts.PeonConstants;
import org.sterl.llmpeon.tool.ToolService;

/**
 * Manages the MCP server connection lifecycle for the view.
 * Reads configuration from preferences, schedules background jobs for connecting,
 * and notifies the caller of state changes via a callback.
 */
public class McpConnectionService {

    private final ToolService toolService;
    private final Consumer<Boolean> onStateChange;

    /**
     * @param toolService   the tool registry to connect/disconnect MCP tools on
     * @param onStateChange called on the UI thread with {@code true} when connected,
     *                      {@code false} when disconnected or on failure
     */
    public McpConnectionService(ToolService toolService, Consumer<Boolean> onStateChange) {
        this.toolService = toolService;
        this.onStateChange = onStateChange;
    }

    /**
     * Reads current preferences and connects or disconnects MCP as needed.
     * Should be called whenever the preference page is saved.
     */
    public void applyConfig() {
        var servers = McpPreferenceInitializer.loadServers();
        boolean hasMcpServers = !servers.isEmpty();
        boolean enabled = hasMcpServers && McpPreferenceInitializer.isMcpEnabled();
        if (enabled) {
            connect();
        } else {
            disconnect();
        }
    }

    /**
     * Persists the enabled state and connects or disconnects accordingly.
     * Called by the MCP toggle button.
     */
    public void toggle(boolean enabled) {
        McpPreferenceInitializer.setMcpEnabled(enabled);
        if (enabled) {
            connect();
        } else {
            disconnect();
        }
    }

    /** Connects to all configured MCP servers in a background Job. */
    public void connect() {
        Job.create("Connecting MCP servers", monitor -> {
            final AtomicBoolean connected = new AtomicBoolean(false);
            try {
                var servers = McpPreferenceInitializer.loadServers();
                if (!servers.isEmpty()) {
                    toolService.connectMcp(servers);
                    connected.set(true);
                }
                return PeonConstants.okStatus("MCP connected to " + servers.size() + " server(s)");
            } catch (Exception e) {
                return PeonConstants.errorStatus("MCP failed to connect: " + e.getMessage(), e);
            } finally {
                boolean state = connected.get();
                McpPreferenceInitializer.setMcpEnabled(state);
                onStateChange.accept(state);
            }
        }).schedule();
    }

    /** Synchronously disconnects all MCP servers and removes their tools. */
    public void disconnect() {
        toolService.disconnectMcp();
    }

    /**
     * Returns whether any MCP servers are configured in preferences.
     */
    public static boolean hasConfiguredServers() {
        return !McpPreferenceInitializer.loadServers().isEmpty();
    }

    /**
     * Returns whether MCP is both enabled and configured.
     */
    public static boolean isEnabled() {
        List<McpServerConfig> servers = McpPreferenceInitializer.loadServers();
        return !servers.isEmpty() && McpPreferenceInitializer.isMcpEnabled();
    }
}
