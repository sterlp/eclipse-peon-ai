package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sterl.llmpeon.mcp.CodegraphProbe;
import org.sterl.llmpeon.mcp.CodegraphServerFactory;
import org.sterl.llmpeon.mcp.DefaultCodegraphProbe;
import org.sterl.llmpeon.mcp.McpServerConfig;
import org.sterl.llmpeon.mcp.McpService;
import org.sterl.llmpeon.parts.ai.CodegraphConnectionService;
import org.sterl.llmpeon.parts.ai.CodegraphConnectionService.Kind;
import org.sterl.llmpeon.tool.ToolService;

/**
 * R-CG-1/3/4 + ADR-0069: the built-in Codegraph lifecycle owner — hermetic via a fake probe,
 * a createMcpService seam (canned fakes), recorder registries and a proxy IProject (apply() is
 * the synchronous seam); plus one optional real end-to-end apply (assumeTrue on binary + index,
 * CI-safe). The owner creates + connects the service exactly once and is its only closer;
 * the registries only adopt/detach.
 */
public class CodegraphConnectionServiceTest {

    private Path dirA;
    private Path dirB;
    private Path binary;
    private FakeProbe probe;
    private RecordingToolService shared;
    private RecordingToolService po;
    private AtomicReference<IProject> projectRef;
    private RecordingService service;

    @Before
    public void setUp() throws Exception {
        dirA = Files.createTempDirectory("codegraph-a");
        dirB = Files.createTempDirectory("codegraph-b");
        binary = Files.createTempFile("codegraph", "");
        probe = new FakeProbe(Optional.of(binary), dirA);
        shared = new RecordingToolService();
        po = new RecordingToolService();
        projectRef = new AtomicReference<>(projectProxy("projA", dirA.toString()));
        service = new RecordingService(projectRef::get, List.of(shared, po), probe);
    }

    @After
    public void tearDown() throws Exception {
        deleteRecursively(dirA);
        deleteRecursively(dirB);
        Files.deleteIfExists(binary);
    }

    /** R-CG-1: GIVEN binary + index and an active project WHEN apply THEN both registries
     *  adopt the SAME connected service built with the project's workDir (one process, ADR-0069). */
    @Test
    public void givenInstalledAndIndexed_whenProjectActive_thenBothRegistriesConnectCodegraphWithWorkDir() {
        // WHEN
        service.apply();

        // THEN: exactly one service was created, connected once, and adopted into BOTH registries
        assertEquals(1, service.createdFakes.size());
        var fake = service.createdFakes.get(0);
        assertEquals(1, fake.connectCalls);
        assertSame("both registries must adopt the same service instance", fake, shared.adopted.get(CodegraphConnectionService.GROUP));
        assertSame(fake, po.adopted.get(CodegraphConnectionService.GROUP));
        // AND: the adopted config is the expected codegraph config
        assertCodegraphConfig(fake.configured.get(0), binary, dirA);
        assertEquals(Kind.CONNECTED, service.state().kind());
        assertTrue(service.state().project() != null && service.state().project().getName().equals("projA"));
    }

    /** R-CG-1: GIVEN a connected codegraph WHEN the user MCP path disconnects (toggle off)
     *  THEN only the user group is touched — codegraph stays connected. */
    @Test
    public void givenMcpToggleOffPath_whenApplied_thenCodegraphUntouched() {
        // GIVEN connected (apply() itself detaches the group first — group replace, so
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

    /** R-CG-2: GIVEN no binary but an index WHEN apply THEN the start is STILL attempted —
     *  the config falls back to the PATH command (the binary is a hint, not a gate). */
    @Test
    public void givenNoBinary_whenApply_thenSpawnStillAttemptedWithPathCommand() {
        probe = new FakeProbe(Optional.empty(), dirA);
        service = new RecordingService(projectRef::get, List.of(shared, po), probe);

        // WHEN
        service.apply();

        // THEN: a service was created and connected — the spawn was attempted
        assertEquals(1, service.createdFakes.size());
        assertEquals(1, service.createdFakes.get(0).connectCalls);
        // AND: the config used the PATH fallback, not a binary
        var cfg = service.createdFakes.get(0).configured.get(0);
        if (isWindows()) {
            assertEquals("cmd", cfg.command());
            assertEquals("/c codegraph serve --mcp", cfg.args());
        } else {
            assertEquals("codegraph", cfg.command());
            assertEquals("serve --mcp", cfg.args());
        }
        assertEquals(dirA.toString(), cfg.workingDirectory());
        assertEquals(Kind.CONNECTED, service.state().kind());
    }

    /** R-CG-2: GIVEN the index but no binary anywhere WHEN apply and the spawn fails THEN
     *  ERROR with the reason in the tooltip — not OFF, no silent give-up. OFF only means
     *  "no index / no active project". Mutation: remove the index check from apply() → the
     *  no-index part below goes red. */
    @Test
    public void spawnFailureIsError() {
        // GIVEN: index present, binary hint negative, the spawn will fail
        probe = new FakeProbe(Optional.empty(), dirA);
        service = new RecordingService(projectRef::get, List.of(shared, po), probe);
        service.nextConnectFailure = new RuntimeException("No such file or directory");

        // WHEN
        var status = service.apply();

        // THEN: ERROR (not OFF) with the failure reason in the tooltip
        assertEquals(Kind.ERROR, service.state().kind());
        assertTrue("ERROR tooltip must carry the start failure",
                service.state().tooltip().contains("codegraph could not be started"));
        assertTrue(service.state().tooltip().contains("No such file or directory"));
        // AND: the spawn was actually attempted (create + connect), then cleaned up
        assertEquals(1, service.createdFakes.size());
        assertEquals(1, service.createdFakes.get(0).connectCalls);
        assertEquals(1, service.createdFakes.get(0).disconnectCalls);
        assertTrue(shared.disconnectedGroups.contains(CodegraphConnectionService.GROUP));
        assertTrue(po.disconnectedGroups.contains(CodegraphConnectionService.GROUP));
        assertEquals("apply must return an error status", IStatus.ERROR, status.getCode());

        // AND: without an index the OFF path is intact — no spawn attempt at all
        probe = new FakeProbe(Optional.empty(), null);
        service = new RecordingService(projectRef::get, List.of(shared, po), probe);
        service.apply();
        assertEquals(Kind.OFF, service.state().kind());
        assertTrue(service.createdFakes.isEmpty());
    }

    /** R-CG-1/2: GIVEN binary but no index WHEN apply THEN OFF and zero connects. */
    @Test
    public void givenNoIndex_whenApply_thenOffStateAndZeroConnects() {
        probe = new FakeProbe(Optional.of(binary), null);
        service = new RecordingService(projectRef::get, List.of(shared, po), probe);

        // WHEN
        service.apply();

        // THEN: no service was even created, nothing adopted
        assertEquals(Kind.OFF, service.state().kind());
        assertTrue("OFF tooltip must tell the user to init the index",
                service.state().tooltip().contains("codegraph init"));
        assertTrue(service.createdFakes.isEmpty());
        assertTrue(shared.adopted.isEmpty());
        assertTrue(po.adopted.isEmpty());
    }

    /** R-CG-3: GIVEN a connect failure WHEN apply THEN ERROR with the reason, the codegraph
     *  group is cleaned on ALL registries, the owner closes the failed service, and the user
     *  group stays untouched. */
    @Test
    public void givenConnectFails_whenApply_thenErrorStateWithReasonCodegraphGroupCleanedOnAllRegistriesUserGroupUntouched() {
        service.nextConnectFailure = new RuntimeException("boom");

        // WHEN
        service.apply();

        // THEN: ERROR state carrying the failure reason
        assertEquals(Kind.ERROR, service.state().kind());
        assertEquals("codegraph could not be started: boom (installed? PATH?)", service.state().reason());
        // AND: symmetric cleanup on both registries (codegraph group only)
        assertTrue(shared.disconnectedGroups.contains(CodegraphConnectionService.GROUP));
        assertTrue(po.disconnectedGroups.contains(CodegraphConnectionService.GROUP));
        // AND: the owner closed the failed service exactly once
        assertEquals(1, service.createdFakes.get(0).disconnectCalls);
        // AND: the user group was never touched
        assertFalse(shared.userDisconnected);
        assertFalse(po.userDisconnected);
    }

    /** R-CG-4: GIVEN connected to A WHEN the project changes to B THEN A is closed by the
     *  owner and B's service is adopted on both registries (each connected once). */
    @Test
    public void givenConnectedA_whenProjectB_thenDisconnectAConnectB() {
        // GIVEN connected to A
        service.apply();

        // WHEN the active project changes to B
        projectRef.set(projectProxy("projB", dirB.toString()));
        service.apply();

        // THEN: A's service was closed by the owner, B's service adopted on both registries
        assertEquals(2, service.createdFakes.size());
        assertEquals(1, service.createdFakes.get(0).disconnectCalls);
        var fakeB = service.createdFakes.get(1);
        assertEquals(1, fakeB.connectCalls);
        assertSame(fakeB, shared.adopted.get(CodegraphConnectionService.GROUP));
        assertSame(fakeB, po.adopted.get(CodegraphConnectionService.GROUP));
        assertCodegraphConfig(fakeB.configured.get(0), binary, dirB);
        assertTrue(shared.disconnectedGroups.contains(CodegraphConnectionService.GROUP));
        assertTrue(po.disconnectedGroups.contains(CodegraphConnectionService.GROUP));
        assertEquals(Kind.CONNECTED, service.state().kind());
        assertEquals("projB", service.state().project().getName());
    }

    /** R-CG-4: GIVEN a stable connected state WHEN apply runs again THEN no reconnect churn. */
    @Test
    public void givenSameState_whenApplyAgain_thenNoReconnect() {
        // GIVEN connected (apply() detaches the group first — group replace)
        service.apply();
        var sharedDisconnectsBefore = shared.disconnectedGroups.size();
        var poDisconnectsBefore = po.disconnectedGroups.size();

        // WHEN apply runs again with the unchanged project
        service.apply();

        // THEN: exactly one connect() in total, no NEW disconnect (no churn)
        assertEquals(1, service.createdFakes.size());
        assertEquals(1, service.createdFakes.get(0).connectCalls);
        assertEquals(sharedDisconnectsBefore, shared.disconnectedGroups.size());
        assertEquals(poDisconnectsBefore, po.disconnectedGroups.size());
    }

    /** R-CG-4: GIVEN A, then B, then A again WHEN two applies happen THEN the final state is
     *  A and every generation was connected exactly once and closed exactly once (no churn). */
    @Test
    public void givenA_B_A_Flap_whenTwoApplies_thenFinalStateA_NoDoubleConnectA() {
        // GIVEN connected to A
        service.apply();

        // WHEN the project flaps A -> B -> A (two applies)
        projectRef.set(projectProxy("projB", dirB.toString()));
        service.apply();
        projectRef.set(projectProxy("projA", dirA.toString()));
        service.apply();

        // THEN: three generations, each connected once, the two old ones closed once
        assertEquals(3, service.createdFakes.size());
        for (var fake : service.createdFakes) assertEquals(1, fake.connectCalls);
        assertEquals(1, service.createdFakes.get(0).disconnectCalls);
        assertEquals(1, service.createdFakes.get(1).disconnectCalls);
        assertEquals(0, service.createdFakes.get(2).disconnectCalls);
        assertCodegraphConfig(service.createdFakes.get(0).configured.get(0), binary, dirA);
        assertCodegraphConfig(service.createdFakes.get(1).configured.get(0), binary, dirB);
        assertCodegraphConfig(service.createdFakes.get(2).configured.get(0), binary, dirA);
        assertEquals(Kind.CONNECTED, service.state().kind());
        assertEquals("projA", service.state().project().getName());
    }

    /** GIVEN a connected server that sent instructions WHEN apply THEN they are captured
     *  for the turn context. */
    @Test
    public void givenConnectedWithInstructions_thenInstructionsCaptured() {
        service.cannedInstructions = "CG-INST";

        // WHEN
        service.apply();

        // THEN: the instructions ride in the live state (captured at connect)
        assertEquals("CG-INST", service.state().instructions());
    }

    /** R-CG-8: GIVEN each state kind WHEN tooltip THEN the user-readable text matches. */
    @Test
    public void givenEachStateKind_whenTooltip_thenExpectedText() {
        // CONNECTED
        service.apply();
        assertTrue(service.state().tooltip().contains("projA"));
        assertTrue(service.state().tooltip().contains("codegraph_explore"));

        // OFF (index case)
        probe = new FakeProbe(Optional.of(binary), null);
        service = new RecordingService(projectRef::get, List.of(shared, po), probe);
        service.apply();
        assertTrue(service.state().tooltip().contains("Codegraph OFF"));
        assertTrue(service.state().tooltip().contains("codegraph init"));

        // ERROR
        probe = new FakeProbe(Optional.of(binary), dirA);
        shared = new RecordingToolService();
        po = new RecordingToolService();
        service = new RecordingService(projectRef::get, List.of(shared, po), probe);
        service.nextConnectFailure = new RuntimeException("boom");
        service.apply();
        assertTrue(service.state().tooltip().contains("Codegraph error"));
        assertTrue(service.state().tooltip().contains("boom"));
    }

    /** Optional E2E (skipped without binary/index): a REAL connection (real McpService) for
     *  the indexed repo — tool list AND instructions visible. */
    @Test
    public void givenRealBinaryAndIndexedRepo_whenApply_thenRealToolVisibleWithInstructions() throws Exception {
        var realProbe = new DefaultCodegraphProbe();
        var repoRoot = repoRoot();
        assumeTrue("codegraph binary not installed", realProbe.findBinary().isPresent());
        assumeTrue("no codegraph index at or above the repo root", realProbe.findIndexWorkDir(repoRoot).isPresent());

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
            assertTrue("instructions expected from the real server", realService.state().instructions() != null);
        } finally {
            // ADR-0069: the owner closes the real process — a registry detach alone would leak it
            realService.disconnect();
        }
    }

    /** ADR-0069 (Paul-(a)): GIVEN an active project WHEN apply THEN exactly one connect()
     *  in total and every registry holds the same service instance. */
    @Test
    public void givenProjectActive_whenApply_thenOneConnectAdoptedIntoEveryRegistry() {
        // WHEN
        service.apply();

        // THEN: one connect in total, every registry adopted the same instance
        assertEquals(1, service.createdFakes.size());
        assertEquals(1, service.createdFakes.get(0).connectCalls);
        var fake = service.createdFakes.get(0);
        for (var registry : List.of(shared, po)) {
            assertSame("every registry must hold the same service instance",
                    fake, registry.adopted.get(CodegraphConnectionService.GROUP));
        }
        assertEquals(Kind.CONNECTED, service.state().kind());
    }

    private static void assertCodegraphConfig(McpServerConfig cfg, Path expectedBinary, Path expectedWorkDir) {
        assertEquals(CodegraphServerFactory.NAME, cfg.name());
        assertEquals(McpServerConfig.McpTransportType.STDIO, cfg.type());
        assertEquals(expectedBinary.toString(), cfg.command());
        assertEquals("serve --mcp", cfg.args());
        assertEquals(expectedWorkDir.toString(), cfg.workingDirectory());
        assertEquals("no telemetry env (R-CG-10)", "", cfg.envVars());
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
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

    /** Canned probe: binary hint + index workDir configurable per case (R-CG-2). */
    private static class FakeProbe implements CodegraphProbe {
        private final Optional<Path> binary;
        private final Path indexWorkDir; // null = no index anywhere

        FakeProbe(Optional<Path> binary, Path indexWorkDir) {
            this.binary = binary;
            this.indexWorkDir = indexWorkDir;
        }

        @Override
        public Optional<Path> findBinary() {
            return binary;
        }

        @Override
        public Optional<Path> findIndexWorkDir(Path projectDiskPath) {
            return indexWorkDir == null ? Optional.empty() : Optional.of(indexWorkDir);
        }
    }

    /**
     * ADR-0069 seam: the createMcpService factory is swapped for canned fakes (config
     * captured, connect/disconnect counted, optional failure, canned instructions).
     */
    private static class RecordingService extends CodegraphConnectionService {
        final List<FakeMcpService> createdFakes = new java.util.ArrayList<>();
        RuntimeException nextConnectFailure;
        String cannedInstructions;

        RecordingService(java.util.function.Supplier<IProject> projectRef, List<ToolService> registries, CodegraphProbe probe) {
            super(projectRef, registries, probe);
        }

        @Override
        public McpService createMcpService(McpServerConfig config) {
            var fake = new FakeMcpService(List.of(config));
            fake.failure = nextConnectFailure;
            fake.instructions = cannedInstructions;
            createdFakes.add(fake);
            return fake;
        }
    }

    /** Canned McpService: no real transport, counts connect/disconnect, canned specs + instructions. */
    private static class FakeMcpService extends McpService {
        final List<McpServerConfig> configured;
        RuntimeException failure;
        String instructions;
        int connectCalls;
        int disconnectCalls;

        FakeMcpService(List<McpServerConfig> configured) {
            super(configured);
            this.configured = configured;
        }

        @Override
        public void connect() {
            connectCalls++;
            if (failure != null) throw failure;
        }

        @Override
        public java.util.List<dev.langchain4j.agent.tool.ToolSpecification> getToolSpecifications() {
            return List.of(dev.langchain4j.agent.tool.ToolSpecification.builder()
                    .name("codegraph_explore").description("explore").build());
        }

        @Override
        public boolean hasTool(String toolName) {
            return "codegraph_explore".equals(toolName);
        }

        @Override
        public Optional<String> instructionsOf(String serverName) {
            return Optional.ofNullable(instructions);
        }

        @Override
        public void disconnect() {
            disconnectCalls++;
        }
    }

    /**
     * Recorder registry: captures group-scoped adoptions/detaches without opening a real
     * MCP connection; the no-arg disconnectMcp() models the user MCP path (toggle off).
     */
    private static class RecordingToolService extends ToolService {
        final Map<String, McpService> adopted = new HashMap<>();
        final List<String> disconnectedGroups = new java.util.ArrayList<>();
        boolean userDisconnected;

        RecordingToolService() {
            super(false);
        }

        @Override
        public void adoptMcpGroup(String group, McpService service) {
            adopted.put(group, service);
        }

        @Override
        public void disconnectMcp(String group) {
            disconnectedGroups.add(group);
        }

        @Override
        public void disconnectMcp() {
            userDisconnected = true;
        }
    }
}
