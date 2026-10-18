package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sterl.llmpeon.mcp.CodegraphServerFactory;
import org.sterl.llmpeon.mcp.McpServerConfig;
import org.sterl.llmpeon.mcp.McpService;
import org.sterl.llmpeon.parts.PeonConstants;
import org.sterl.llmpeon.parts.config.McpConnectionService;
import org.sterl.llmpeon.parts.config.McpPreferenceInitializer;
import org.sterl.llmpeon.tool.ToolService;

/**
 * R-MCP1 + ADR-0069: MCP config changes are applied live, unchanged config does not reconnect,
 * and the service is the OWNER of the user-group McpService (create + connect once, only
 * closer; registries adopt/detach only). Two seams: {@link RecordingService} (connect()/
 * disconnect() counted, no registries) and {@link SeamRecordingService} (createMcpService
 * swapped for canned fakes, real connectServers()/disconnect()/applyConfig() flow).
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

    @Test
    public void givenToggledState_whenApplied_thenNoReconnect() {
        // GIVEN servers are configured but MCP is disabled; applyConfig has applied that state S
        var c1 = List.of(server("ctx"));
        givenMcpConfig(c1, false);
        var service = new RecordingService();
        service.applyConfig();
        assertEquals(0, service.connectCount);
        assertEquals(1, service.disconnectCount);

        // WHEN the user toggles MCP on — its own connect fires, and the preference
        // change it persists immediately fires an applyConfig() event
        service.toggle(true);
        assertEquals(1, service.connectCount);

        // THEN the preference event's applyConfig() does NOT reconnect again,
        // because toggle() already advanced lastApplied to the new state
        service.applyConfig();
        assertEquals(1, service.connectCount);
        assertEquals(1, service.disconnectCount);
    }

    /** ADR-0067/0069 (Paul-(a)): GIVEN two registries wired into the service WHEN
     *  connectServers() THEN both adopt the SAME service, connected once, with exactly C1. */
    @Test
    public void givenTwoRegistries_whenConnected_thenBothReceiveSameServerList() {
        // GIVEN: MCP config C1 and two registries (shared + po) wired into the service
        var c1 = List.of(server("ctx"));
        givenMcpConfig(c1, true);
        var shared = new RecordingToolService();
        var po = new RecordingToolService();
        var service = new SeamRecordingService(List.of(shared, po), state -> {});

        // WHEN: the synchronous connect seam runs
        service.connectServers();

        // THEN: both registries adopted the same service, connected once, with exactly C1
        assertEquals(1, service.createdFakes.size());
        var fake = service.createdFakes.get(0);
        assertEquals(1, fake.connectCalls);
        assertEquals(c1, fake.configured);
        assertSame(fake, shared.adopted.get(ToolService.USER_GROUP));
        assertSame(fake, po.adopted.get(ToolService.USER_GROUP));
    }

    /** ADR-0067/0069: GIVEN a connected user group WHEN disconnect() THEN both registries
     *  are detached AND the owner closed the service exactly once. */
    @Test
    public void givenTwoRegistries_whenDisconnected_thenBothCleared() {
        // GIVEN: connected via the real flow
        var c1 = List.of(server("ctx"));
        givenMcpConfig(c1, true);
        var shared = new RecordingToolService();
        var po = new RecordingToolService();
        var service = new SeamRecordingService(List.of(shared, po), state -> {});
        service.connectServers();
        var fake = service.createdFakes.get(0);

        // WHEN
        service.disconnect();

        // THEN: both registries recorded the detach and the owner closed the service once
        assertTrue("shared registry must be disconnected", shared.disconnected);
        assertTrue("po registry must be disconnected", po.disconnected);
        assertEquals(1, fake.disconnectCalls);
    }

    /** R-CG-11: GIVEN a manual entry with the reserved built-in name WHEN connectServers()
     *  THEN the entry is skipped (built-in wins) and only the other servers connect. */
    @Test
    public void givenManualCodegraphEntry_whenConnectServers_thenSkippedNoDuplicate() {
        // GIVEN: config with the reserved built-in name plus a regular server
        var codegraph = new McpServerConfig(CodegraphServerFactory.NAME, McpServerConfig.McpTransportType.STDIO,
                "", "", "", "/usr/bin/true", "serve --mcp", "", "");
        var docs = server("docs");
        givenMcpConfig(List.of(codegraph, docs), true);
        var shared = new RecordingToolService();
        var po = new RecordingToolService();
        var service = new SeamRecordingService(List.of(shared, po), state -> {});

        // WHEN: the synchronous connect seam runs
        service.connectServers();

        // THEN: only the non-reserved server was adopted into both registries (no codegraph duplicate)
        assertEquals(1, service.createdFakes.size());
        assertEquals(List.of(docs), service.createdFakes.get(0).configured);
        assertSame(service.createdFakes.get(0), shared.adopted.get(ToolService.USER_GROUP));
        assertSame(service.createdFakes.get(0), po.adopted.get(ToolService.USER_GROUP));
    }

    /** ADR-0069: GIVEN connected C1 WHEN the config changes to C2 and connectServers() runs
     *  THEN all-or-nothing replace — old closed once, new connected once and adopted. */
    @Test
    public void givenConnected_whenConfigChangesThenConnectServers_thenOldServiceClosedNewAdoptedOnce() {
        // GIVEN: connected with C1
        var c1 = List.of(server("ctx"));
        givenMcpConfig(c1, true);
        var shared = new RecordingToolService();
        var po = new RecordingToolService();
        var service = new SeamRecordingService(List.of(shared, po), state -> {});
        service.connectServers();
        var fake1 = service.createdFakes.get(0);

        // WHEN the config changes to C2 and connectServers() runs again
        var c2 = List.of(server("context7"));
        givenMcpConfig(c2, true);
        service.connectServers();

        // THEN: the old service was closed by the owner, the new one connected once + adopted
        assertEquals(2, service.createdFakes.size());
        assertEquals(1, fake1.disconnectCalls);
        var fake2 = service.createdFakes.get(1);
        assertEquals(1, fake2.connectCalls);
        assertEquals(c2, fake2.configured);
        assertSame(fake2, shared.adopted.get(ToolService.USER_GROUP));
        assertSame(fake2, po.adopted.get(ToolService.USER_GROUP));
    }

    /** ADR-0069 (real reachable empty path): GIVEN connected WHEN the server list becomes
     *  empty and applyConfig() runs THEN desiredState() = ([], false) → real disconnect() →
     *  both registries detached and the service closed once (no leak). */
    @Test
    public void givenConnected_whenServerListBecomesEmpty_thenServiceClosedAndRegistriesDetached() {
        // GIVEN: connected with one server
        var c1 = List.of(server("ctx"));
        givenMcpConfig(c1, true);
        var shared = new RecordingToolService();
        var po = new RecordingToolService();
        var service = new SeamRecordingService(List.of(shared, po), state -> {});
        service.connectServers();
        var fake1 = service.createdFakes.get(0);
        assertEquals(1, fake1.connectCalls);

        // WHEN the server list becomes empty and applyConfig() runs
        givenMcpConfig(List.of(), true);
        service.applyConfig();

        // THEN: both registries detached and the owner closed the service exactly once
        assertTrue("shared registry must be disconnected", shared.disconnected);
        assertTrue("po registry must be disconnected", po.disconnected);
        assertEquals(1, fake1.disconnectCalls);
    }

    /** ADR-0069 (defensive seam): GIVEN connected WHEN connectServers() runs directly with
     *  an empty list THEN detach-first still applies — old service closed once, registries
     *  detached, state false, and the status tells the truth. */
    @Test
    public void givenConnected_whenConnectServersWithEmptyList_thenDetachedAndClosed() {
        // GIVEN: connected with one server
        var c1 = List.of(server("ctx"));
        givenMcpConfig(c1, true);
        var states = new ArrayList<Boolean>();
        var shared = new RecordingToolService();
        var po = new RecordingToolService();
        var service = new SeamRecordingService(List.of(shared, po), states::add);
        service.connectServers();
        var fake1 = service.createdFakes.get(0);

        // WHEN the server list becomes empty and connectServers() runs directly
        givenMcpConfig(List.of(), true);
        var status = service.connectServers();

        // THEN: old service closed once, both registries detached, state false reported
        assertEquals(1, fake1.disconnectCalls);
        assertTrue("shared registry must be disconnected", shared.disconnected);
        assertTrue("po registry must be disconnected", po.disconnected);
        assertEquals(List.of(true, false), states);
        // AND: the status is honest about the empty config
        assertTrue("status: " + status.getMessage(), status.getMessage().contains("disconnected"));
    }

    private McpServerConfig server(String name) {
        return new McpServerConfig(name, McpServerConfig.McpTransportType.HTTP,
                "https://" + name + ".example.com/mcp", "", "", "", "", "", "");
    }

    /**
     * Recorder seam (tests 1–3): overrides connect()/disconnect() so no real connection is
     * opened. The registry list is empty — the recorder never references it.
     */
    private static class RecordingService extends McpConnectionService {
        int connectCount;
        int disconnectCount;
        List<McpServerConfig> lastConnectedServers;

        RecordingService() {
            super(List.of(), enabled -> {});
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

    /**
     * ADR-0069 seam (tests 4–9): only the createMcpService factory is swapped for canned
     * fakes — connectServers()/disconnect()/applyConfig() run the REAL flow.
     */
    private static class SeamRecordingService extends McpConnectionService {
        final List<FakeMcpService> createdFakes = new ArrayList<>();

        SeamRecordingService(List<ToolService> toolServices, java.util.function.Consumer<Boolean> onStateChange) {
            super(toolServices, onStateChange);
        }

        @Override
        public McpService createMcpService(List<McpServerConfig> servers) {
            var fake = new FakeMcpService(servers);
            createdFakes.add(fake);
            return fake;
        }
    }

    /** Canned McpService: no real transport, counts connect/disconnect, keeps the config. */
    private static class FakeMcpService extends McpService {
        final List<McpServerConfig> configured;
        int connectCalls;
        int disconnectCalls;

        FakeMcpService(List<McpServerConfig> configured) {
            super(configured);
            this.configured = configured;
        }

        @Override
        public void connect() {
            connectCalls++;
        }

        @Override
        public void disconnect() {
            disconnectCalls++;
        }
    }

    /**
     * Recorder registry: captures the adopted service per group and the no-arg disconnect
     * (the user MCP path), so no real MCP connection is opened.
     */
    private static class RecordingToolService extends ToolService {
        final Map<String, McpService> adopted = new HashMap<>();
        boolean disconnected;

        RecordingToolService() {
            super(false);
        }

        @Override
        public void adoptMcpGroup(String group, McpService service) {
            adopted.put(group, service);
        }

        @Override
        public void disconnectMcp() {
            disconnected = true;
        }
    }
}
