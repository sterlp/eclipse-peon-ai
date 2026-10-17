package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sterl.llmpeon.mcp.CodegraphProbe;
import org.sterl.llmpeon.mcp.CodegraphServerFactory;
import org.sterl.llmpeon.mcp.DefaultCodegraphProbe;
import org.sterl.llmpeon.mcp.McpServerConfig;
import org.sterl.llmpeon.parts.ai.CodegraphConnectionService;
import org.sterl.llmpeon.parts.ai.CodegraphConnectionService.Kind;
import org.sterl.llmpeon.tool.ToolService;

/**
 * R-CG-1/3/4: the built-in Codegraph lifecycle owner — hermetic via a fake probe, recorder
 * registries and a proxy IProject (apply() is the synchronous seam); plus one optional real
 * end-to-end apply (assumeTrue on binary + index, CI-safe).
 */
public class CodegraphConnectionServiceTest {

    private Path dirA;
    private Path dirB;
    private Path binary;
    private FakeProbe probe;
    private RecordingToolService shared;
    private RecordingToolService po;
    private AtomicReference<IProject> projectRef;
    private CodegraphConnectionService service;

    @Before
    public void setUp() throws Exception {
        dirA = Files.createTempDirectory("codegraph-a");
        dirB = Files.createTempDirectory("codegraph-b");
        binary = Files.createTempFile("codegraph", "");
        probe = new FakeProbe(Optional.of(binary), true);
        shared = new RecordingToolService();
        po = new RecordingToolService();
        projectRef = new AtomicReference<>(projectProxy("projA", dirA.toString()));
        service = new CodegraphConnectionService(projectRef::get, List.of(shared, po), probe);
    }

    @After
    public void tearDown() throws Exception {
        deleteRecursively(dirA);
        deleteRecursively(dirB);
        Files.deleteIfExists(binary);
    }

    /** R-CG-1: GIVEN binary + index and an active project WHEN apply THEN both registries
     *  connect the codegraph group with the project's workDir. */
    @Test
    public void givenInstalledAndIndexed_whenProjectActive_thenBothRegistriesConnectCodegraphWithWorkDir() {
        // WHEN
        service.apply();

        // THEN: both registries received exactly the expected config
        assertEquals(1, shared.connectedConfigs.size());
        assertEquals(1, po.connectedConfigs.size());
        assertCodegraphConfig(shared.connectedConfigs.get(0), binary, dirA);
        assertCodegraphConfig(po.connectedConfigs.get(0), binary, dirA);
        assertEquals(Kind.CONNECTED, service.state().kind());
        assertTrue(service.state().project() != null && service.state().project().getName().equals("projA"));
    }

    /** R-CG-1: GIVEN a connected codegraph WHEN the user MCP path disconnects (toggle off)
     *  THEN only the user group is touched — codegraph stays connected. */
    @Test
    public void givenMcpToggleOffPath_whenApplied_thenCodegraphUntouched() {
        // GIVEN connected (apply() itself disconnects the group first — group replace, so
        // snapshot the disconnect counts BEFORE the user path runs)
        service.apply();
        assertEquals(Kind.CONNECTED, service.state().kind());
        var sharedDisconnectsBefore = shared.disconnectedGroups.size();
        var poDisconnectsBefore = po.disconnectedGroups.size();

        // WHEN the user MCP disconnect path runs (what McpConnectionService.disconnect() does)
        shared.disconnectMcp();
        po.disconnectMcp();

        // THEN: only the user group was disconnected — no NEW codegraph-group disconnect, state intact
        assertTrue(shared.userDisconnected);
        assertTrue(po.userDisconnected);
        assertEquals("user path must not disconnect the codegraph group",
                sharedDisconnectsBefore, shared.disconnectedGroups.size());
        assertEquals(poDisconnectsBefore, po.disconnectedGroups.size());
        assertEquals(Kind.CONNECTED, service.state().kind());
    }

    /** R-CG-1/2: GIVEN no binary WHEN apply THEN OFF and zero connect calls. */
    @Test
    public void givenNoBinary_whenApply_thenOffStateAndZeroConnects() {
        probe = new FakeProbe(Optional.empty(), true);
        service = new CodegraphConnectionService(projectRef::get, List.of(shared, po), probe);

        // WHEN
        service.apply();

        // THEN
        assertEquals(Kind.OFF, service.state().kind());
        assertTrue(shared.connectedConfigs.isEmpty());
        assertTrue(po.connectedConfigs.isEmpty());
        assertFalse(service.state().tooltip().contains("codegraph init"));
    }

    /** R-CG-1/2: GIVEN binary but no index WHEN apply THEN OFF and zero connect calls. */
    @Test
    public void givenNoIndex_whenApply_thenOffStateAndZeroConnects() {
        probe = new FakeProbe(Optional.of(binary), false);
        service = new CodegraphConnectionService(projectRef::get, List.of(shared, po), probe);

        // WHEN
        service.apply();

        // THEN
        assertEquals(Kind.OFF, service.state().kind());
        assertTrue("OFF tooltip must tell the user to init the index",
                service.state().tooltip().contains("codegraph init"));
        assertTrue(shared.connectedConfigs.isEmpty());
        assertTrue(po.connectedConfigs.isEmpty());
    }

    /** R-CG-3: GIVEN a connect failure WHEN apply THEN ERROR with the reason, the codegraph
     *  group is cleaned on ALL registries and the user group stays untouched. */
    @Test
    public void givenConnectFails_whenApply_thenErrorStateWithReasonCodegraphGroupCleanedOnAllRegistriesUserGroupUntouched() {
        shared.failConnect = true;

        // WHEN
        service.apply();

        // THEN: ERROR state carrying the failure reason
        assertEquals(Kind.ERROR, service.state().kind());
        assertEquals("boom", service.state().reason());
        // AND: symmetric cleanup on both registries (codegraph group only)
        assertTrue(shared.disconnectedGroups.contains(CodegraphConnectionService.GROUP));
        assertTrue(po.disconnectedGroups.contains(CodegraphConnectionService.GROUP));
        // AND: the user group was never touched
        assertFalse(shared.userDisconnected);
        assertFalse(po.userDisconnected);
    }

    /** R-CG-4: GIVEN connected to A WHEN the project changes to B THEN A is disconnected
     *  and B is connected with B's workDir. */
    @Test
    public void givenConnectedA_whenProjectB_thenDisconnectAConnectB() {
        // GIVEN connected to A
        service.apply();

        // WHEN the active project changes to B
        projectRef.set(projectProxy("projB", dirB.toString()));
        service.apply();

        // THEN: A's connection was replaced by B's on both registries
        assertTrue(shared.disconnectedGroups.contains(CodegraphConnectionService.GROUP));
        assertTrue(po.disconnectedGroups.contains(CodegraphConnectionService.GROUP));
        assertEquals(2, shared.connectedConfigs.size());
        assertCodegraphConfig(shared.connectedConfigs.get(1), binary, dirB);
        assertCodegraphConfig(po.connectedConfigs.get(1), binary, dirB);
        assertEquals(Kind.CONNECTED, service.state().kind());
        assertEquals("projB", service.state().project().getName());
    }

    /** R-CG-4: GIVEN a stable connected state WHEN apply runs again THEN no reconnect churn. */
    @Test
    public void givenSameState_whenApplyAgain_thenNoReconnect() {
        // GIVEN connected (apply() disconnects the group first — group replace)
        service.apply();
        var sharedDisconnectsBefore = shared.disconnectedGroups.size();
        var poDisconnectsBefore = po.disconnectedGroups.size();

        // WHEN apply runs again with the unchanged project
        service.apply();

        // THEN: exactly one connect per registry, no NEW disconnect (no churn)
        assertEquals(1, shared.connectedConfigs.size());
        assertEquals(1, po.connectedConfigs.size());
        assertEquals(sharedDisconnectsBefore, shared.disconnectedGroups.size());
        assertEquals(poDisconnectsBefore, po.disconnectedGroups.size());
    }

    /** R-CG-4: GIVEN A, then B, then A again WHEN two applies happen THEN the final state is
     *  A and A was reconnected exactly once during the flap (no extra churn). */
    @Test
    public void givenA_B_A_Flap_whenTwoApplies_thenFinalStateA_NoDoubleConnectA() {
        // GIVEN connected to A
        service.apply();
        assertEquals(1, shared.connectedConfigs.size());

        // WHEN the project flaps A -> B -> A (two applies)
        projectRef.set(projectProxy("projB", dirB.toString()));
        service.apply();
        projectRef.set(projectProxy("projA", dirA.toString()));
        service.apply();

        // THEN: final state A, and A was connected exactly twice overall (initial + flap-back)
        assertEquals(Kind.CONNECTED, service.state().kind());
        assertEquals(3, shared.connectedConfigs.size());
        assertCodegraphConfig(shared.connectedConfigs.get(0), binary, dirA);
        assertCodegraphConfig(shared.connectedConfigs.get(1), binary, dirB);
        assertCodegraphConfig(shared.connectedConfigs.get(2), binary, dirA);
        assertEquals("projA", service.state().project().getName());
    }

    /** GIVEN a connected server that sent instructions WHEN apply THEN they are captured
     *  for the turn context. */
    @Test
    public void givenConnectedWithInstructions_thenInstructionsCaptured() {
        shared.cannedInstructions = "CG-INST";

        // WHEN
        service.apply();

        // THEN: the first registry's instructions are exposed
        assertEquals(Optional.of("CG-INST"), service.instructions());
    }

    /** R-CG-7: GIVEN connected WHEN projectInfoLine THEN it names project, disk path,
     *  codegraph_explore and the disk→Eclipse path mapping; OFF/ERROR return "". */
    @Test
    public void givenConnected_whenProjectInfoLine_thenContainsProjectAndPathMapping() {
        // WHEN connected
        service.apply();

        // THEN
        var line = service.projectInfoLine();
        assertTrue("line must name the project: " + line, line.contains("projA"));
        assertTrue("line must carry the disk path: " + line, line.contains(dirA.toString()));
        assertTrue("line must hint the tool: " + line, line.contains("codegraph_explore"));
        assertTrue("line must show the Eclipse path mapping: " + line, line.contains("/projA"));

        // AND: OFF → empty
        probe = new FakeProbe(Optional.of(binary), false);
        service = new CodegraphConnectionService(projectRef::get, List.of(shared, po), probe);
        service.apply();
        assertEquals("", service.projectInfoLine());

        // AND: ERROR → empty
        probe = new FakeProbe(Optional.of(binary), true);
        shared = new RecordingToolService();
        shared.failConnect = true;
        po = new RecordingToolService();
        service = new CodegraphConnectionService(projectRef::get, List.of(shared, po), probe);
        service.apply();
        assertEquals("", service.projectInfoLine());
    }

    /** R-CG-8: GIVEN each state kind WHEN tooltip THEN the user-readable text matches. */
    @Test
    public void givenEachStateKind_whenTooltip_thenExpectedText() {
        // CONNECTED
        service.apply();
        assertTrue(service.state().tooltip().contains("projA"));
        assertTrue(service.state().tooltip().contains("codegraph_explore"));

        // OFF (index case)
        probe = new FakeProbe(Optional.of(binary), false);
        service = new CodegraphConnectionService(projectRef::get, List.of(shared, po), probe);
        service.apply();
        assertTrue(service.state().tooltip().contains("Codegraph OFF"));
        assertTrue(service.state().tooltip().contains("codegraph init"));

        // ERROR
        probe = new FakeProbe(Optional.of(binary), true);
        shared = new RecordingToolService();
        shared.failConnect = true;
        po = new RecordingToolService();
        service = new CodegraphConnectionService(projectRef::get, List.of(shared, po), probe);
        service.apply();
        assertTrue(service.state().tooltip().contains("Codegraph error"));
        assertTrue(service.state().tooltip().contains("boom"));
    }

    /** Optional E2E (skipped without binary/index): a REAL ToolService connects the real
     *  `codegraph serve --mcp` for the indexed repo — tool list AND instructions visible. */
    @Test
    public void givenRealBinaryAndIndexedRepo_whenApply_thenRealToolVisibleWithInstructions() throws Exception {
        var realProbe = new DefaultCodegraphProbe();
        var repoRoot = repoRoot();
        assumeTrue("codegraph binary not installed", realProbe.findBinary().isPresent());
        assumeTrue("no codegraph index at repo root", realProbe.hasIndex(repoRoot));

        var registry = new ToolService(false);
        var realService = new CodegraphConnectionService(
                () -> projectProxy("repo", repoRoot.toString()), List.of(registry), realProbe);
        try {
            // WHEN the real connection is applied
            realService.apply();

            // THEN: connected, the real tool is listed and real instructions were captured
            assertEquals(Kind.CONNECTED, realService.state().kind());
            assertTrue("expected codegraph_explore in " + registry.mcpToolNames(),
                    registry.mcpToolNames().contains("codegraph_explore"));
            assertTrue("instructions expected from the real server", realService.instructions().isPresent());
        } finally {
            registry.disconnectMcp(CodegraphConnectionService.GROUP);
        }
    }

    private static void assertCodegraphConfig(McpServerConfig cfg, Path expectedBinary, Path expectedWorkDir) {
        assertEquals(CodegraphServerFactory.NAME, cfg.name());
        assertEquals(McpServerConfig.McpTransportType.STDIO, cfg.type());
        assertEquals(expectedBinary.toString(), cfg.command());
        assertEquals("serve --mcp", cfg.args());
        assertEquals(expectedWorkDir.toString(), cfg.workingDirectory());
        assertEquals("no telemetry env (R-CG-10)", "", cfg.envVars());
    }

    private static IProject projectProxy(String name, String diskPath) {
        return (IProject) Proxy.newProxyInstance(IProject.class.getClassLoader(),
                new Class<?>[] { IProject.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getRawLocation" -> IPath.fromOSString(diskPath);
                    case "getLocation" -> IPath.fromOSString(diskPath);
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "IProject(" + name + ")";
                    default -> primitiveDefault(method.getReturnType());
                });
    }

    private static Object primitiveDefault(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == long.class) return 0L;
        if (type == double.class) return 0d;
        if (type == float.class) return 0f;
        return 0;
    }

    private static Path repoRoot() throws Exception {
        var dir = Path.of(CodegraphConnectionServiceTest.class
                .getProtectionDomain().getCodeSource().getLocation().toURI());
        while (dir != null && !Files.exists(dir.resolve(".git"))) dir = dir.getParent();
        return dir;
    }

    private static void deleteRecursively(Path dir) throws Exception {
        if (dir == null || !Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> p.toFile().delete());
        }
    }

    /** Canned probe: binary/index presence configurable per case. */
    private static class FakeProbe implements CodegraphProbe {
        private final Optional<Path> binary;
        private final boolean indexed;

        FakeProbe(Optional<Path> binary, boolean indexed) {
            this.binary = binary;
            this.indexed = indexed;
        }

        @Override
        public Optional<Path> findBinary() {
            return binary;
        }

        @Override
        public boolean hasIndex(Path projectDiskPath) {
            return indexed;
        }
    }

    /**
     * Recorder registry: captures group-scoped connects/disconnects without opening a real
     * MCP connection; the no-arg disconnectMcp() models the user MCP path (toggle off).
     */
    private static class RecordingToolService extends ToolService {
        final List<McpServerConfig> connectedConfigs = new ArrayList<>();
        final List<String> disconnectedGroups = new ArrayList<>();
        boolean userDisconnected;
        boolean failConnect;
        String cannedInstructions;

        RecordingToolService() {
            super(false);
        }

        @Override
        public void connectMcp(String group, McpServerConfig server) {
            if (failConnect) throw new RuntimeException("boom");
            connectedConfigs.add(server);
        }

        @Override
        public void disconnectMcp(String group) {
            disconnectedGroups.add(group);
        }

        @Override
        public void disconnectMcp() {
            userDisconnected = true;
        }

        @Override
        public Optional<String> mcpInstructions(String group, String serverName) {
            return Optional.ofNullable(cannedInstructions);
        }
    }
}
