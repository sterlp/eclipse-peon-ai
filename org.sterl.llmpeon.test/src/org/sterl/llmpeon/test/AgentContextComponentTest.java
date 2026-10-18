package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.util.List;

import org.junit.Test;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.model.TaggedResult;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.command.CommandService;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.parts.ai.CodegraphConnectionService.Kind;
import org.sterl.llmpeon.parts.ai.CodegraphConnectionService.State;
import org.sterl.llmpeon.parts.ai.CodegraphContextItem;
import org.sterl.llmpeon.parts.ai.component.AgentContextComponent;
import org.sterl.llmpeon.parts.ai.component.SharedToolsComponent;
import org.sterl.llmpeon.parts.tools.PlanTool;
import org.sterl.llmpeon.parts.tools.memory.WorkspaceMemoryTool;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.scaffold.AiScaffoldAgent;
import org.sterl.llmpeon.shared.StringUtil;

/**
 * Inc 2 (PeonAiService-Struktur-Aufräumen): AgentContextComponent — turn-context assembly,
 * Env-only static bake, handoff-line one-shot consumption and plan reference — without LLM.
 */
public class AgentContextComponentTest extends AbstractIntegrationTest {

    private WorkspaceMemoryTool wmt;
    private AgentContextComponent sut;
    private AiAgent activeAgent;
    private ConfiguredChatModel ccm;
    private SharedToolsComponent sharedTools;
    private org.sterl.llmpeon.context.UserContext userContext;
    private AiScaffoldAgent scaffold;

    @org.junit.Before
    public void beforeEach() {
        doSetup();
    }

    private void doSetup() {
        // GIVEN: a component wired with the real shared tools (no LLM involved)
        ccm = new ConfiguredChatModel(LlmConfig.builder()
                .model("test").url("http://localhost:0")
                .configDir(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), ".peon-test")).build());
        var skillService = new SkillService();
        sharedTools = new SharedToolsComponent(skillService, new CommandService());
        wmt = sharedTools.workspaceMemoryTool();
        wmt.memoryReset();

        userContext = new org.sterl.llmpeon.context.UserContext();
        userContext.setCurrentProject(project);
        scaffold = new AiScaffoldAgent(ccm);
        activeAgent = new org.sterl.llmpeon.agent.AiDevAgent(ccm, sharedTools.toolService());

        sut = new AgentContextComponent(() -> project, wmt, userContext, scaffold,
                sharedTools.toolService(), () -> activeAgent,
                ccm::getConfig, List::of, offItem());
    }

    /** GIVEN a set handoff line WHEN turnContext() twice THEN first call contains it, second does not. */
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

    /** GIVEN an existing plan file WHEN turnContext() THEN an item whose dedupKey (block start line)
     *  carries the path is present; without plan none. */
    @Test
    public void test_turnContext_planReference_onlyWhenPlanExists() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        eclipseWriteFile("/test_project/" + PlanTool.OVERVIEW_FILE, "# A Plan");

        // WHEN a plan exists on disk
        var items = sut.turnContext();

        // THEN the plan-reference item carries the path as dedupKey
        var planItem = items.stream().filter(i -> StringUtil.hasValue(i.dedupKey())
                && i.dedupKey().contains(PlanTool.OVERVIEW_FILE)).findAny();
        assertTrue("plan reference item expected", planItem.isPresent());
        assertNotNull(planItem.get().dedupKey());

        // AND: without a plan file there is no item
        eclipseDeleteResource("/test_project/" + PlanTool.OVERVIEW_FILE);
        var withoutPlan = sut.turnContext();
        assertFalse(withoutPlan.stream().anyMatch(i -> i.dedupKey() != null
                && i.dedupKey().contains(PlanTool.OVERVIEW_FILE)));
    }

    /** GIVEN agents WHEN initStaticContext THEN static context is exactly Env — never the memory. */
    @Test
    public void test_initStaticContext_envOnly() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        // GIVEN: memory has content AND a capturing agent is in the agent list
        wmt.memoryAdd("must not appear in static context");
        var capturingAgent = new org.sterl.llmpeon.agent.AiDevAgent(
                new ConfiguredChatModel(LlmConfig.builder()
                        .model("test").url("http://localhost:0").build()),
                new org.sterl.llmpeon.tool.ToolService());
        var sutWithAgents = new AgentContextComponent(() -> project, wmt,
                new org.sterl.llmpeon.context.UserContext(), null, null,
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
     *  non-scaffold agent, renders the instructions, and carries a non-null label (R-CG-12)
     *  and a non-null dedupKey (state-change dedup). */
    // R-CG-7
    @Test
    public void test_turnContext_codegraphItem_presentWhenConnected() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        // GIVEN a component whose codegraph item is CONNECTED with server instructions
        var connectedItem = new CodegraphContextItem(
                () -> new State(Kind.CONNECTED, project, null, "CG-SERVER-DOCS"));
        var sutConnected = new AgentContextComponent(() -> project, wmt,
                new org.sterl.llmpeon.context.UserContext(), null, null,
                () -> activeAgent, () -> LlmConfig.builder().model("t").url("http://x").build(),
                List::of, connectedItem);

        // WHEN
        var items = sutConnected.turnContext();

        // THEN the item is present, renders the instructions, and carries label + dedupKey
        var cgItem = items.stream().filter(i -> i instanceof CodegraphContextItem).findFirst();
        assertTrue("codegraph item expected", cgItem.isPresent());
        assertTrue("renders the instructions: " + cgItem.get().render(),
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

        // GIVEN a component whose ACTIVE agent is the scaffold (a CONNECTED item would be present
        // for a non-scaffold agent)
        var scaffoldSut = new AgentContextComponent(() -> project, wmt, userContext, scaffold,
                sharedTools.toolService(), () -> scaffold, ccm::getConfig, List::of,
                new CodegraphContextItem(
                        () -> new State(Kind.CONNECTED, project, null, "CG-SERVER-DOCS")));

        // WHEN
        var items = scaffoldSut.turnContext();

        // THEN no codegraph item at all (the scaffold branch skips it)
        assertFalse("scaffold must not get the codegraph item",
                items.stream().anyMatch(i -> i instanceof CodegraphContextItem));
    }

    private List<String> renderAll(List<ContextItem> items) {
        return items.stream()
                .map(i -> {
                    TaggedResult r = i.render();
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
