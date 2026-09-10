package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sterl.llmpeon.mcp.McpServerConfig;
import org.sterl.llmpeon.parts.PeonConstants;
import org.sterl.llmpeon.parts.config.McpConnectionService;
import org.sterl.llmpeon.parts.config.McpPreferenceInitializer;

/**
 * R-MCP1: MCP config changes are applied live, and unchanged config does not reconnect.
 * Uses a recorder subclass as the seam so no real MCP connection / OSGi tool wiring is needed
 * (ToolService is passed as null — the recorder never touches it).
 */
public class McpConnectionServiceTest {

    private IEclipsePreferences node;
    private String originalServers;
    private String originalEnabled;

    @Before
    public void setUp() {
        node = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
        originalServers = node.get(PeonConstants.PREF_MCP_SERVERS, null);
        originalEnabled = node.get(PeonConstants.PREF_MCP_ENABLED, null);
    }

    @After
    public void tearDown() {
        if (originalServers == null) node.remove(PeonConstants.PREF_MCP_SERVERS);
        else node.put(PeonConstants.PREF_MCP_SERVERS, originalServers);
        if (originalEnabled == null) node.remove(PeonConstants.PREF_MCP_ENABLED);
        else node.put(PeonConstants.PREF_MCP_ENABLED, originalEnabled);
        try { node.flush(); } catch (Exception ignored) {}
    }

    private void givenMcpConfig(List<McpServerConfig> servers, boolean enabled) {
        McpPreferenceInitializer.saveServers(servers);
        McpPreferenceInitializer.setMcpEnabled(enabled);
    }

    @Test
    public void givenChangedMcpConfig_whenApplied_thenReconnects() {
        // GIVEN MCP connected with config C1
        var c1 = List.of(server("ctx"));
        givenMcpConfig(c1, true);
        var service = new RecordingService();
        service.applyConfig();
        assertEquals(1, service.connectCount);
        assertEquals(c1, service.lastConnectedServers);

        // WHEN the stored MCP config changes to C2 (LlmConfig unchanged — not modelled here)
        var c2 = List.of(server("context7"));
        givenMcpConfig(c2, true);

        // THEN the clients are reconnected with C2
        service.applyConfig();
        assertEquals(2, service.connectCount);
        assertEquals(c2, service.lastConnectedServers);
    }

    @Test
    public void givenUnchangedMcpConfig_whenApplied_thenNoReconnect() {
        // GIVEN MCP connected with config C1
        var c1 = List.of(server("ctx"));
        givenMcpConfig(c1, true);
        var service = new RecordingService();
        service.applyConfig();
        assertEquals(1, service.connectCount);

        // WHEN applyConfig runs again with an unchanged MCP config
        service.applyConfig();

        // THEN no reconnect and no disconnect (no spawn per preference event)
        assertEquals(1, service.connectCount);
        assertEquals(0, service.disconnectCount);
    }

    private McpServerConfig server(String name) {
        return new McpServerConfig(name, McpServerConfig.McpTransportType.HTTP,
                "https://" + name + ".example.com/mcp", "", "", "", "", "");
    }

    /**
     * Recorder seam: overrides connect()/disconnect() so no real connection is opened.
     * ToolService is null — the recorder never references it.
     */
    private static class RecordingService extends McpConnectionService {
        int connectCount;
        int disconnectCount;
        List<McpServerConfig> lastConnectedServers;

        RecordingService() {
            super(null, enabled -> {});
        }

        @Override
        public void connect() {
            connectCount++;
            lastConnectedServers = McpPreferenceInitializer.loadServers();
        }

        @Override
        public void disconnect() {
            disconnectCount++;
        }
    }
}
