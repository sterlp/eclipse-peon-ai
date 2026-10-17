package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.util.List;
import java.util.Optional;

import org.junit.Test;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.command.CommandService;
import org.sterl.llmpeon.context.ContextItem;
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
                ccm::getConfig, List::of, () -> Optional.empty());
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

    /** GIVEN an existing plan file WHEN turnContext() THEN an item with dedupKey = path is present; without plan none. */
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
                () -> List.of(capturingAgent), () -> Optional.empty());

        // WHEN
        sutWithAgents.initStaticContext();

        // THEN: static context exists and is Env-only
        var ctx = capturingAgent.getStaticContext();
        assertEquals(1, ctx.size());
        assertNotNull(ctx.get(0).render());
        assertFalse("memory snapshot must not be baked into the static context",
                ctx.get(0).render().contains("must not appear in static context"));
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

    /** R-CG-7: GIVEN a connected codegraph (supplier delivers the server instructions)
     *  WHEN turnContext() THEN the item is present, renders the text verbatim, carries the
     *  label and dedups by content (no dedup key). */
    // R-CG-7
    @Test
    public void test_turnContext_codegraphInstructionsItem_presentWithText() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        // GIVEN a component whose codegraph instructions supplier delivers server text
        var sutWithInstructions = new AgentContextComponent(() -> project, wmt,
                new org.sterl.llmpeon.context.UserContext(), null, null,
                () -> activeAgent, () -> LlmConfig.builder().model("t").url("http://x").build(),
                List::of, () -> Optional.of("CG-SERVER-DOCS"));

        // WHEN
        var items = sutWithInstructions.turnContext();

        // THEN the item exists, renders the text verbatim and carries the label
        var cgItem = items.stream()
                .filter(i -> "Codegraph instructions".equals(i.label())).findFirst();
        assertTrue("codegraph instructions item expected", cgItem.isPresent());
        assertEquals("CG-SERVER-DOCS", cgItem.get().render());
        assertNull("dedup by content: no dedup key", cgItem.get().dedupKey());
    }

    /** R-CG-7: GIVEN codegraph not connected (empty supplier) WHEN turnContext()
     *  THEN no visible codegraph instructions item (null render = silently skipped). */
    // R-CG-7
    @Test
    public void test_turnContext_codegraphInstructionsItem_absentWhenSupplierEmpty() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        // GIVEN the setup component (empty codegraph instructions supplier)
        // WHEN
        var items = sut.turnContext();

        // THEN no item with the codegraph label has a renderable text
        assertFalse(items.stream().anyMatch(i -> "Codegraph instructions".equals(i.label())
                && i.render() != null));
    }

    /** R-CG-7: GIVEN the scaffold agent WHEN turnContext() THEN the scaffold never
     *  receives the codegraph instructions item (own registry without MCP). */
    // R-CG-7
    @Test
    public void test_turnContext_scaffoldAgent_neverGetsCodegraphInstructionsItem() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        // GIVEN a component whose ACTIVE agent is the scaffold (non-empty supplier)
        var scaffoldSut = new AgentContextComponent(() -> project, wmt, userContext, scaffold,
                sharedTools.toolService(), () -> scaffold, ccm::getConfig, List::of,
                () -> Optional.of("CG-SERVER-DOCS"));

        // WHEN
        var items = scaffoldSut.turnContext();

        // THEN no codegraph item at all (the scaffold branch skips it)
        assertFalse(items.stream().anyMatch(i -> "Codegraph instructions".equals(i.label())));
    }

    private List<String> renderAll(List<ContextItem> items) {
        return items.stream()
                .map(i -> {
                    String r = i.render();
                    return r == null ? "" : i.dedupKey() == null ? r : i.dedupKey() + "\n" + r;
                })
                .toList();
    }

    @Override
    public void after() {
        if (wmt != null) wmt.memoryReset();
        super.after();
    }
}
