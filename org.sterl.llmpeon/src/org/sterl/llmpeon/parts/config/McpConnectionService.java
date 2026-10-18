package org.sterl.llmpeon.parts.config;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.jobs.Job;
import org.sterl.llmpeon.mcp.CodegraphServerFactory;
import org.sterl.llmpeon.mcp.McpServerConfig;
import org.sterl.llmpeon.mcp.McpService;
import org.sterl.llmpeon.parts.PeonConstants;
import org.sterl.llmpeon.tool.ToolService;

/**
 * Manages the MCP server connection lifecycle for the view.
 * Reads configuration from preferences, schedules background jobs for connecting,
 * and notifies the caller of state changes via a callback.
 *
 * <p>ADR-0069: this service is the OWNER of the user-group {@link McpService} — it creates it,
 * calls {@code connect()} exactly once and is its only closer (owner closes last, after
 * detaching every registry). The registries merely ADOPT the service
 * ({@code adoptMcpGroup}), so exactly one process exists per server configuration.
 */
public class McpConnectionService {

    private static final ILog LOG = Platform.getLog(McpConnectionService.class);

    /** ADR-0067: every agent-facing registry MCP connects to (shared + Peon-PO's own). */
    private final List<ToolService> toolServices;
    private final Consumer<Boolean> onStateChange;

    /** Last applied MCP state (server list + effective enabled flag). UI-thread confined. */
    private volatile McpApplied lastApplied;
    /** ADR-0069: the owned, currently-live user-group service (this service is its only closer). */
    private volatile McpService liveService;

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
            var loaded = McpPreferenceInitializer.loadServers();
            // R-CG-11: the name "codegraph" belongs to the built-in — reserved, ALWAYS filtered
            // (decision 2026-10-17: no state coupling of the user path to the built-in).
            var reserved = loaded.stream().filter(s -> CodegraphServerFactory.NAME.equals(s.name())).count();
            if (reserved > 0) {
                LOG.info("skipping reserved built-in MCP server name '" + CodegraphServerFactory.NAME
                        + "' — managed by the Codegraph built-in, remove the entry from the MCP config");
            }
            var servers = loaded.stream()
                    .filter(s -> !CodegraphServerFactory.NAME.equals(s.name()))
                    .toList();
            // ADR-0069: detach FIRST, always — an empty list is a pure disconnect path (no leak)
            detachUserGroup();
            if (!servers.isEmpty()) {
                var service = createMcpService(servers);
                service.connect(); // throws → group already detached, nothing adopted, liveService null
                for (var registry : toolServices) registry.adoptMcpGroup(ToolService.USER_GROUP, service);
                liveService = service;
                connected.set(true);
            }
            return PeonConstants.okStatus(servers.isEmpty()
                    ? "MCP disconnected (no servers configured)"
                    : "MCP connected to " + servers.size() + " server(s)");
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

    /** Synchronously disconnects all MCP servers and removes their tools — on every registry.
     *  ADR-0069: detaches the registries and closes the owned service exactly once. */
    public void disconnect() {
        detachUserGroup();
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

    /** ADR-0069: the one detach sequence — the registries NEVER close, the owner closes the service. */
    private void detachUserGroup() {
        for (var registry : toolServices) registry.disconnectMcp();
        closeLiveService();
    }

    /**
     * ADR-0069 test seam: swappable McpService factory (tests adopt canned fakes).
     */
    public McpService createMcpService(List<McpServerConfig> servers) {
        return new McpService(servers);
    }

    /** ADR-0069: the owner closes the previous generation — volatile read, null out, disconnect. */
    private void closeLiveService() {
        var s = liveService;
        liveService = null;
        if (s != null) s.disconnect();
    }
}
