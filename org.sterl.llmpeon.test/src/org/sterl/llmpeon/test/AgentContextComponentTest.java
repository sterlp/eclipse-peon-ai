package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.Test;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.model.ContextInput;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.command.CommandService;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.context.UserContext;
import org.sterl.llmpeon.parts.ai.CodegraphConnectionService.Kind;
import org.sterl.llmpeon.parts.ai.CodegraphConnectionService.State;
import org.sterl.llmpeon.parts.ai.CodegraphContextItem;
import org.sterl.llmpeon.parts.ai.component.AgentContextComponent;
import org.sterl.llmpeon.parts.ai.component.SharedToolsComponent;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.parts.tools.PlanFileView;
import org.sterl.llmpeon.parts.tools.PlanTool;
import org.sterl.llmpeon.parts.tools.memory.WorkspaceMemoryTool;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.scaffold.AiScaffoldAgent;

/** Turn assembly and static-context integration tests; see docs/user-context.md (UC-SEL-6) and docs/plan-tool.md (UC-PT-3). */
public class AgentContextComponentTest extends AbstractIntegrationTest {

    private WorkspaceMemoryTool wmt;
    private AgentContextComponent sut;
    private AiAgent activeAgent;
    private ConfiguredChatModel ccm;
    private SharedToolsComponent sharedTools;
    private UserContext userContext;
    private AiScaffoldAgent scaffold;
    private final PlanFileView planView = new PlanFileView();
    private final List<String> planProblems = new ArrayList<>();

    @org.junit.Before
    public void beforeEach() {
        doSetup();
    }

    private void doSetup() {
        ccm = new ConfiguredChatModel(LlmConfig.builder()
                .model("test").url("http://localhost:0")
                .configDir(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), ".peon-test")).build());
        var skillService = new SkillService();
        sharedTools = new SharedToolsComponent(skillService, new CommandService());
        wmt = sharedTools.workspaceMemoryTool();
        wmt.memoryReset();

        userContext = new UserContext();
        userContext.setCurrentProject(project);
        scaffold = new AiScaffoldAgent(ccm);
        activeAgent = new AiDevAgent(ccm, sharedTools.toolService());

        sut = new AgentContextComponent(() -> project, planFile(), wmt, userContext, scaffold,
                sharedTools.toolService(), () -> activeAgent,
                ccm::getConfig, List::of, offItem());
    }

    /** See docs/plan-tool.md, R-PT-3. */
    private Supplier<IFile> planFile() {
        return () -> planView.visible(project, null, planProblems::add);
    }

    private String projectInfoItem(List<ContextItem> items, String projectName) {
        var match = items.stream()
                .filter(i -> ("Project info " + projectName).equals(i.label()))
                .toList();
        assertEquals("exactly one Project info " + projectName + " item expected, got: " + match.size(),
                1, match.size());
        var rendered = match.get(0).render();
        assertNotNull("project info item must render", rendered);
        return rendered.body();
    }

    private static String lineContaining(String body, String prefix) {
        for (var line : body.split(System.lineSeparator())) {
            if (line.startsWith(prefix)) return line;
        }
        return null;
    }

    @Test
    public void test_turnContext_handoffLine_consumedOnce() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        // GIVEN
        sut.armHandoffLine("Peon-Plan", "/some/plan.md");

        // WHEN
        var first = renderAll(sut.turnContext());
        var second = renderAll(sut.turnContext());

        // THEN
        assertTrue(first.stream().anyMatch(t -> t.contains("Handover from Peon-Plan")));
        assertFalse("handoff line must be consumed once",
                second.stream().anyMatch(t -> t.contains("Handover from Peon-Plan")));
    }

    /** GIVEN agents WHEN initStaticContext THEN static context is exactly Env — never the memory. */
    @Test
    public void test_initStaticContext_envOnly() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        // GIVEN: memory has content AND a capturing agent is in the agent list
        wmt.memoryAdd("must not appear in static context");
        var capturingAgent = new AiDevAgent(
                new ConfiguredChatModel(LlmConfig.builder()
                        .model("test").url("http://localhost:0").build()),
                new org.sterl.llmpeon.tool.ToolService());
        var sutWithAgents = new AgentContextComponent(() -> project, () -> null, wmt,
                new UserContext(), null, null,
                () -> activeAgent, () -> LlmConfig.builder().model("t").url("http://x").build(),
                () -> List.of(capturingAgent), offItem());

        // WHEN
        sutWithAgents.initStaticContext();

        // THEN: static context exists and is Env-only
        var ctx = capturingAgent.getStaticContext();
        assertEquals(1, ctx.size());
        assertNotNull(ctx.get(0).render());
        assertFalse("memory snapshot must not be baked into the static context",
                ctx.get(0).render().body().contains("must not appear in static context"));
    }

    /** GIVEN empty memory WHEN turnContext() THEN no workspace-memory item is injected. */
    @Test
    public void test_turnContext_emptyMemory_noMemoryItem() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        // GIVEN empty memory (reset in setup)
        // WHEN
        var rendered = renderAll(sut.turnContext());

        // THEN
        assertFalse(rendered.stream().anyMatch(t -> t.contains("workspace-memory")));
    }

    /** R-CG-7: GIVEN a connected codegraph WHEN turnContext() THEN the item is present for a
     *  non-scaffold agent, renders the path mapping (NOT the instructions — they stay in the
     *  State as the dedup marker), and carries a non-null label (R-CG-12) and a non-null
     *  dedupKey (state-change dedup). */
    // R-CG-7
    @Test
    public void test_turnContext_codegraphItem_presentWhenConnected() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        // GIVEN a component whose codegraph item is CONNECTED with server instructions
        var connectedItem = new CodegraphContextItem(
                () -> new State(Kind.CONNECTED, project, null, "CG-SERVER-DOCS"));
        var sutConnected = new AgentContextComponent(() -> project, () -> null, wmt,
                new UserContext(), null, null,
                () -> activeAgent, () -> LlmConfig.builder().model("t").url("http://x").build(),
                List::of, connectedItem);

        // WHEN
        var items = sutConnected.turnContext();

        // THEN the item is present, does NOT render the instructions (dedup marker only), and carries label + dedupKey
        var cgItem = items.stream().filter(i -> i instanceof CodegraphContextItem).findFirst();
        assertTrue("codegraph item expected", cgItem.isPresent());
        assertFalse("instructions never rendered (R-CG-7(c)): " + cgItem.get().render(),
                cgItem.get().render().body().contains("CG-SERVER-DOCS"));
        assertNotNull("label drives the R-CG-12 chat signal", cgItem.get().label());
        assertNotNull("state-change dedup needs a key", cgItem.get().dedupKey());
    }

    /** R-TR-4 (SOLL 2026-10-18): GIVEN codegraph OFF WHEN turnContext() THEN the item renders
     *  nothing — no chat line, the state stays visible via the icon + tooltip only. */
    // R-TR-4
    @Test
    public void test_turnContext_codegraphItem_offRendersNothing() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        // GIVEN the setup component (OFF codegraph item, no project)
        // WHEN
        var items = sut.turnContext();

        // THEN the codegraph item is present but renders nothing and carries no key
        var cgItem = items.stream().filter(i -> i instanceof CodegraphContextItem).findFirst();
        assertTrue("codegraph item expected even when OFF", cgItem.isPresent());
        assertNull("OFF renders nothing (R-TR-4)", cgItem.get().render());
        assertNull("OFF carries no dedup key (no block)", cgItem.get().dedupKey());
    }

    /** R-CG-7: GIVEN the scaffold agent WHEN turnContext() THEN the scaffold never
     *  receives the codegraph item (own registry without MCP). */
    // R-CG-7
    @Test
    public void test_turnContext_scaffoldAgent_neverGetsCodegraphItem() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        // GIVEN a component whose ACTIVE agent is the scaffold
        var scaffoldSut = new AgentContextComponent(() -> project, () -> null, wmt, userContext, scaffold,
                sharedTools.toolService(), () -> scaffold, ccm::getConfig, List::of,
                new CodegraphContextItem(
                        () -> new State(Kind.CONNECTED, project, null, "CG-SERVER-DOCS")));

        // WHEN
        var items = scaffoldSut.turnContext();

        // THEN no codegraph item at all (the scaffold branch skips it)
        assertFalse("scaffold must not get the codegraph item",
                items.stream().anyMatch(i -> i instanceof CodegraphContextItem));
    }

    // UC-SEL-6
    // UC-PT-3
    @Test
    public void diskWrittenPlanVisibleInTurnContext() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        var planDir = PeonTestFixture.dir().toPath().resolve(PlanTool.PLAN_DIR);
        Files.createDirectories(planDir);
        var overview = planDir.resolve("overview.md");
        Files.writeString(overview, "# disk written plan\n", StandardCharsets.UTF_8);

        assertNull("AGENTS.md must not already exist in fixture: " + project.getFile("AGENTS.md").getFullPath(),
                project.findMember("AGENTS.md"));
        eclipseWriteFile("AGENTS.md", "# agents md\n");
        try {
            assertFalse("fixture must leave the workspace tree stale",
                    project.getFile(PlanTool.OVERVIEW_FILE).exists());

            var items = sut.turnContext();

            var body = projectInfoItem(items, project.getName());

            assertTrue("headline must start with PROJECT_TAG: " + body,
                    body.startsWith(UserContext.PROJECT_TAG + project.getName()));

            var eclipseLine = lineContaining(body, "Eclipse path: ");
            assertNotNull("Eclipse path line missing", eclipseLine);
            assertTrue("Eclipse path hint missing: " + eclipseLine,
                    eclipseLine.contains("→ eclipse* tools (all workspace files)"));
            assertTrue("Eclipse path must contain project path: " + eclipseLine,
                    eclipseLine.contains(JdtUtil.pathOf(project)));

            var diskLine = lineContaining(body, "Disk path: ");
            assertNotNull("Disk path line missing", diskLine);
            assertTrue("Disk path hint missing: " + diskLine,
                    diskLine.contains("→ only disk* and shell workDir"));
            assertTrue("Disk path must contain disk path: " + diskLine,
                    diskLine.contains(JdtUtil.diskPathOf(project)));

            var naturesLine = lineContaining(body, "Natures:      ");
            assertNotNull("Natures line missing", naturesLine);

            var filesLine = lineContaining(body, "Files: ");
            assertNotNull("Files line missing", filesLine);
            assertTrue("Files must contain pom.xml: " + filesLine,
                    filesLine.contains("/test_project/pom.xml"));
            assertTrue("Files must contain AGENTS.md: " + filesLine,
                    filesLine.contains("/test_project/AGENTS.md"));
            assertFalse("Files must NOT contain package.json: " + filesLine,
                    filesLine.contains("package.json"));
            assertFalse("Files must NOT contain docs/index.md: " + filesLine,
                    filesLine.contains("docs/index.md"));

            var planLine = lineContaining(body, "Plan: ");
            assertNotNull("Plan line missing", planLine);
            assertTrue("Plan line must contain overview.md path: " + planLine,
                    planLine.contains(PlanTool.OVERVIEW_FILE));

            var oldPlanItem = items.stream()
                    .filter(i -> i.label() != null && i.label().startsWith("Plan reference"))
                    .findAny();
            assertFalse("old 'Plan reference' item must be gone: " + oldPlanItem.map(ContextItem::label),
                    oldPlanItem.isPresent());

            var allRendered = renderAll(items);
            assertFalse("no 'context: plan' block allowed: " + allRendered,
                    allRendered.stream().anyMatch(t -> t.contains("context: plan")));

            assertTrue("plan problems: " + planProblems, planProblems.isEmpty());
        } finally {
            Files.deleteIfExists(overview);
            Files.deleteIfExists(planDir);
            project.getFolder(PlanTool.PLAN_DIR).refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
        }
    }

    // UC-SEL-6
    @Test
    public void projectInfoOmitsPlanWhenNoPlanExists() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        eclipseWriteFile("/test_project/" + PlanTool.OVERVIEW_FILE, "# A Plan");

        var body1 = projectInfoItem(sut.turnContext(), project.getName());
        var planLine1 = lineContaining(body1, "Plan: ");
        assertNotNull("first turn must have Plan line", planLine1);
        assertTrue("Plan line must contain overview.md: " + planLine1,
                planLine1.contains(PlanTool.OVERVIEW_FILE));

        eclipseDeleteResource("/test_project/" + PlanTool.OVERVIEW_FILE);

        var body2 = projectInfoItem(sut.turnContext(), project.getName());

        var planLine2 = lineContaining(body2, "Plan: ");
        assertNull("second turn must NOT have Plan line: " + planLine2, planLine2);

        assertTrue("second turn must still have project head",
                body2.startsWith(UserContext.PROJECT_TAG + project.getName()));
        var filesLine2 = lineContaining(body2, "Files: ");
        assertNotNull("second turn must still have Files line", filesLine2);
        assertTrue("Files must contain pom.xml: " + filesLine2,
                filesLine2.contains("pom.xml"));
    }

    // UC-SEL-6
    @Test
    public void projectInfoListsAllEntryFilesInDocumentedOrder() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        assertNull("package.json must not already exist in fixture",
                project.findMember("package.json"));
        eclipseWriteFile("package.json", "{}");
        assertNull("AGENTS.md must not already exist in fixture",
                project.findMember("AGENTS.md"));
        eclipseWriteFile("AGENTS.md", "# agents");
        assertNull("docs/index.md must not already exist in fixture",
                project.findMember("docs/index.md"));
        eclipseWriteFile("docs/index.md", "# index");

        var body = projectInfoItem(sut.turnContext(), project.getName());

        var filesLine = lineContaining(body, "Files: ");
        assertNotNull("Files line missing", filesLine);

        var idxPom = filesLine.indexOf("/test_project/pom.xml");
        var idxPackage = filesLine.indexOf("/test_project/package.json");
        var idxAgents = filesLine.indexOf("/test_project/AGENTS.md");
        var idxDocs = filesLine.indexOf("/test_project/docs/index.md");

        assertTrue("pom.xml missing: " + filesLine, idxPom >= 0);
        assertTrue("package.json missing: " + filesLine, idxPackage >= 0);
        assertTrue("AGENTS.md missing: " + filesLine, idxAgents >= 0);
        assertTrue("docs/index.md missing: " + filesLine, idxDocs >= 0);

        assertTrue("pom.xml before package.json: " + filesLine, idxPom < idxPackage);
        assertTrue("package.json before AGENTS.md: " + filesLine, idxPackage < idxAgents);
        assertTrue("AGENTS.md before docs/index.md: " + filesLine, idxAgents < idxDocs);

        var allLines = body.split(System.lineSeparator());
        for (var line : allLines) {
            if (line.equals("/test_project/pom.xml") || line.equals("/test_project/package.json")) {
                throw new AssertionError("bare file line without 'Files:' prefix: " + line);
            }
        }

        assertTrue("headline missing", body.startsWith(UserContext.PROJECT_TAG + project.getName()));
        assertNotNull("Eclipse path line missing", lineContaining(body, "Eclipse path: "));
        assertNotNull("Disk path line missing", lineContaining(body, "Disk path: "));
    }

    // UC-SEL-6
    @Test
    public void projectInfoOmitsFilesLineWhenNoEntryFilesExist() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        var fixture = new NestedProjectFixture();
        fixture.create();
        try {
            var uc = new UserContext();
            uc.setCurrentProject(fixture.leaf());

            var items = uc.get();
            var body = projectInfoItem(items, fixture.leaf().getName());

            var filesLine = lineContaining(body, "Files: ");
            assertNull("no Files line expected on leaf: " + filesLine, filesLine);

            assertTrue("headline must be present",
                    body.startsWith(UserContext.PROJECT_TAG + fixture.leaf().getName()));
            assertNotNull("Eclipse path missing", lineContaining(body, "Eclipse path: "));
            assertNotNull("Disk path missing", lineContaining(body, "Disk path: "));
            assertNotNull("Natures missing", lineContaining(body, "Natures:      "));
            assertTrue("nesting line missing: " + body,
                    body.contains("Nested in Eclipse project"));
        } finally {
            fixture.destroy();
        }
    }

    // UC-SEL-6
    @Test
    public void projectInfoKeepsPlanAndNestingDuringHandoff() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        var fixture = new NestedProjectFixture();
        fixture.create();
        try {
            assertNull("plan must not already exist in leaf fixture",
                    fixture.leaf().findMember(PlanTool.OVERVIEW_FILE));
            eclipseWriteFile("/" + fixture.leaf().getName() + "/" + PlanTool.OVERVIEW_FILE, "# leaf plan");

            var uc = new UserContext();
            uc.setCurrentProject(fixture.leaf());

            var leafPlanView = new PlanFileView();
            var leafPlanProblems = new ArrayList<String>();
            var leafSut = new AgentContextComponent(() -> fixture.leaf(),
                    () -> leafPlanView.visible(fixture.leaf(), null, leafPlanProblems::add),
                    wmt, uc, scaffold, sharedTools.toolService(), () -> activeAgent,
                    ccm::getConfig, List::of, offItem());
            leafSut.armHandoffLine("Plan", "/peon-plan/overview.md");

            var items1 = leafSut.turnContext();
            var bodyFirst = projectInfoItem(items1, fixture.leaf().getName());

            var pIdx1 = bodyFirst.indexOf("Plan: ");
            var nIdx1 = bodyFirst.indexOf("Nested in Eclipse project");
            assertTrue("Plan before nesting, turn 1: " + bodyFirst, pIdx1 >= 0 && pIdx1 < nIdx1);

            var handoffItems1 = items1.stream()
                    .filter(i -> i.label() != null && i.label().startsWith("Handover reference"))
                    .count();
            assertEquals("handoff must be present in turn 1", 1, handoffItems1);

            assertFalse("no old Plan reference item in turn 1",
                    items1.stream().anyMatch(i -> i.label() != null && i.label().startsWith("Plan reference")));

            var items2 = leafSut.turnContext();
            var bodySecond = projectInfoItem(items2, fixture.leaf().getName());

            var pIdx2 = bodySecond.indexOf("Plan: ");
            var nIdx2 = bodySecond.indexOf("Nested in Eclipse project");
            assertTrue("Plan before nesting, turn 2: " + bodySecond, pIdx2 >= 0 && pIdx2 < nIdx2);

            var handoffItems2 = items2.stream()
                    .filter(i -> i.label() != null && i.label().startsWith("Handover reference"))
                    .count();
            assertEquals("handoff must be consumed in turn 2", 0, handoffItems2);

            assertFalse("no old Plan reference item in turn 2",
                    items2.stream().anyMatch(i -> i.label() != null && i.label().startsWith("Plan reference")));
        } finally {
            fixture.destroy();
        }
    }

    @Test
    public void projectInfoNeverMixesAPlanFromAnotherProject() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        var counter = new AtomicInteger(0);
        var uc = new UserContext();
        uc.setCurrentProject(project);

        var fixture = new NestedProjectFixture();
        fixture.create();
        try {
            var planFromTestProject = project.getFile(PlanTool.OVERVIEW_FILE);
            var leafPlan = fixture.leaf().getFile(PlanTool.OVERVIEW_FILE);
            Supplier<IFile> foreignSupplier = () -> {
                counter.incrementAndGet();
                return leafPlan;
            };

            var items = uc.get(foreignSupplier);
            var body = projectInfoItem(items, project.getName());

            assertTrue("head must be test_project: " + body,
                    body.startsWith(UserContext.PROJECT_TAG + project.getName()));
            var planLine = lineContaining(body, "Plan: ");
            assertNull("no Plan line for foreign plan: " + planLine, planLine);
            assertEquals("supplier called exactly once", 1, counter.get());

            var filesLine = lineContaining(body, "Files: ");
            assertNotNull("Files line still present", filesLine);
            assertTrue("Files must contain pom.xml: " + filesLine,
                    filesLine.contains("pom.xml"));

            counter.set(0);
            Supplier<IFile> matchingSupplier = () -> {
                counter.incrementAndGet();
                return planFromTestProject;
            };

            var items2 = uc.get(matchingSupplier);
            var body2 = projectInfoItem(items2, project.getName());
            var planLine2 = lineContaining(body2, "Plan: ");
            assertNotNull("Plan line expected for matching plan", planLine2);
            assertEquals("supplier called exactly once", 1, counter.get());
        } finally {
            fixture.destroy();
        }
    }

    @Test
    public void projectInfoSurvivesPlanQueryRefreshFailure() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        var problems = new ArrayList<String>();
        var failingView = new PlanFileView((resource, depth, monitor) -> {
            throw new CoreException(
                    new org.eclipse.core.runtime.Status(
                            org.eclipse.core.runtime.IStatus.ERROR, "test", "refresh boom"));
        });

        var uc = new UserContext();
        uc.setCurrentProject(project);

        var failingSut = new AgentContextComponent(() -> project,
                () -> failingView.visible(project, null, problems::add),
                wmt, uc, scaffold, sharedTools.toolService(), () -> activeAgent,
                ccm::getConfig, List::of, offItem());

        var items = failingSut.turnContext();
        var body = projectInfoItem(items, project.getName());

        assertTrue("head must be present: " + body,
                body.startsWith(UserContext.PROJECT_TAG + project.getName()));
        var planLine = lineContaining(body, "Plan: ");
        assertNull("no Plan line on refresh failure: " + planLine, planLine);

        var filesLine = lineContaining(body, "Files: ");
        assertNotNull("Files line still present", filesLine);

        assertEquals("exactly one problem expected: " + problems, 1, problems.size());
        var problem = problems.get(0);
        assertTrue("problem must mention plan path: " + problem,
                problem.contains(PlanTool.OVERVIEW_FILE));
        assertTrue("problem must mention cause: " + problem,
                problem.contains("refresh boom"));
    }

    // -- Helpers ---------------------------------------------------------------

    private List<String> renderAll(List<ContextItem> items) {
        return items.stream()
                .map(i -> {
                    ContextInput r = i.render();
                    return r == null ? "" : r.toString();
                })
                .toList();
    }

    /** An OFF codegraph item with no project — the default for tests that don't exercise codegraph. */
    private static CodegraphContextItem offItem() {
        return new CodegraphContextItem(
                () -> new State(Kind.OFF, null, "no active project"));
    }

    @Override
    public void after() {
        if (wmt != null) wmt.memoryReset();
        super.after();
    }
}