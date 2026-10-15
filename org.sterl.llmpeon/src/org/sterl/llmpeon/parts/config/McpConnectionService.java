package org.sterl.llmpeon.parts.config;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.eclipse.core.runtime.IStatus;
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

    /** ADR-0067: every agent-facing registry MCP connects to (shared + Peon-PO's own). */
    private final List<ToolService> toolServices;
    private final Consumer<Boolean> onStateChange;

    /** Last applied MCP state (server list + effective enabled flag). UI-thread confined. */
    private volatile McpApplied lastApplied;

    private record McpApplied(List<McpServerConfig> servers, boolean enabled) {}

    /** The MCP state the current preferences would produce. */
    private McpApplied desiredState() {
        var servers = McpPreferenceInitializer.loadServers();
        return new McpApplied(servers, !servers.isEmpty() && McpPreferenceInitializer.isMcpEnabled());
    }

    /**
     * @param toolServices  the agent-facing registries to connect/disconnect MCP tools on (ADR-0067)
     * @param onStateChange called on the UI thread with {@code true} when connected,
     *                      {@code false} when disconnected or on failure
     */
    public McpConnectionService(List<ToolService> toolServices, Consumer<Boolean> onStateChange) {
        this.toolServices = List.copyOf(toolServices);
        this.onStateChange = onStateChange;
    }

    /**
     * Reads current preferences and connects or disconnects MCP as needed.
     * Should be called whenever the preference page is saved.
     */
    public void applyConfig() {
        var desired = desiredState();
        if (desired.equals(lastApplied)) return;   // R-MCP1b: unchanged config → no reconnect
        applyState(desired);
    }

    /**
     * Persists the enabled state and connects or disconnects accordingly.
     * Called by the MCP toggle button.
     */
    public void toggle(boolean enabled) {
        McpPreferenceInitializer.setMcpEnabled(enabled);
        var desired = desiredState();
        applyState(desired);
    }

    /** Remembers the state and connects or disconnects. No equals-check — the callers own it. */
    private void applyState(McpApplied desired) {
        lastApplied = desired;
        if (desired.enabled()) {
            connect();
        } else {
            disconnect();
        }
    }

    /**
     * Connects to all configured MCP servers on every registry — the synchronous body of the
     * connect job (test seam).
     */
    public IStatus connectServers() {
        final AtomicBoolean connected = new AtomicBoolean(false);
        try {
            var servers = McpPreferenceInitializer.loadServers();
            if (!servers.isEmpty()) {
                for (ToolService registry : toolServices) {
                    registry.connectMcp(servers);
                }
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
    }

    /** Connects to all configured MCP servers in a background Job. */
    public void connect() {
        Job.create("Connecting MCP servers", monitor -> {
            return connectServers();
        }).schedule();
    }

    /** Synchronously disconnects all MCP servers and removes their tools — on every registry. */
    public void disconnect() {
        for (ToolService registry : toolServices) {
            registry.disconnectMcp();
        }
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
