package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.sterl.llmpeon.StandingOrdersBuilder;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.agent.AiPoAgent;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.parts.PeonAiService;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceWriteFileTool;
import org.sterl.llmpeon.parts.tools.PlanTool;
import org.sterl.llmpeon.scaffold.AiScaffoldAgent;
import org.sterl.llmpeon.tool.tools.CompactSessionTool;
import org.sterl.llmpeon.tool.tools.JonDelegateTool;
import org.sterl.llmpeon.tool.tools.DiskFileReadTool;
import org.sterl.llmpeon.tool.tools.DiskFileWriteTool;
import org.sterl.llmpeon.tool.tools.DiskGrepTool;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;

public class PeonAiServiceTest extends AbstractTest {

    PeonAiService aiService = new PeonAiService(null, null, null, null);
    
    private final StandingOrdersBuilder standingOrders = new StandingOrdersBuilder()
            .add(aiService)
            .add(aiService.getAgentsMdService());
    
    @Test
    public void test_compact_tool() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        var compressor = aiService.getToolService().getTool(CompactSessionTool.class);
        assertIsPresent(compressor);
        
        // AND
        var comp = aiService.getToolService().toolSpecifications().stream()
            .filter(t -> t.name().equalsIgnoreCase(CompactSessionTool.NAME))
            .findAny();
        assertIsPresent(comp);
    }
    
    @Test
    public void test_onHandoff() {
        // GIVEN
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.setActiveAgent(aiService.getAgents().stream().filter(a -> a.getName().equals(AiPlanAgent.NAME)).findFirst().orElseThrow());
        // AND
        aiService.getActiveAgent().getMemory().clear();
        aiService.setProject(project);
        aiService.getToolService().getTool(EclipseWorkspaceWriteFileTool.class).get().eclipseDeleteResource("peon-plan");
        
        // WHEN
        assertFalse(aiService.onHandoff());
        // AND
        aiService.getActiveAgent().getMemory().add(AiMessage.from("Very good plan"));
        assertTrue(aiService.onHandoff());
        
        // THEN
        assertEquals(AiDevAgent.NAME, aiService.getActiveAgent().getName());
        assertHasUserMessageWith(aiService.getActiveAgent().getMemory().getCopy(), "Very good plan");
    }

    @Test
    public void test_switch_disk_off() {
        // GIVEN
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.updateConfig(aiService.getConfig().toBuilder().diskToolsEnabled(true).build());
        assertIsPresent(aiService.getToolService().getTool(DiskGrepTool.class));
        assertIsPresent(aiService.getToolService().getTool(DiskFileReadTool.class));
        assertIsPresent(aiService.getToolService().getTool(DiskFileWriteTool.class));
        
        // WHEN
        aiService.updateConfig(aiService.getConfig().toBuilder().diskToolsEnabled(false).build());
        
        // THEN
        assertIsEmpty(aiService.getToolService().getTool(DiskGrepTool.class));
        assertIsEmpty(aiService.getToolService().getTool(DiskFileReadTool.class));
        assertIsEmpty(aiService.getToolService().getTool(DiskFileWriteTool.class));
        
        assertIsPresent(aiService.getToolService().getTool(CompactSessionTool.class));
    }
    
    @Test
    public void test_message_order() throws IOException {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.updateConfig(aiService.getConfig().toBuilder()
                .providerType(AiProvider.OPEN_AI)
                .url(mockLlmServer.getUrl()).build());
        mockLlmServer.queueResponse(AiMessage.aiMessage("Pong"));
        assertTrue(Files.exists(Path.of("../skills")));
        aiService.getSkillService().refresh(Path.of("../skills"));
        
        // WHEN
        aiService.getActiveAgent().call("Ping", null);
        
        // THEN
        var msg = aiService.getActiveAgent().getMemory().getCopy();
        assertEquals("Ping", ((TextContent)((UserMessage)msg.get(0)).contents().getLast()).text());
        assertEquals("Pong", ((AiMessage)msg.get(1)).text());
    }
    
    @Test
    public void test_has_read_skill_tool() throws IOException {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.updateConfig(aiService.getConfig().toBuilder()
                .providerType(AiProvider.OPEN_AI)
                .url(mockLlmServer.getUrl()).build());
        mockLlmServer.queueResponse(AiMessage.aiMessage("Pong"));
        
        // WHEN
        aiService.getActiveAgent().call("Ping", null);
        
        // THEN
        assertNotNull(mockLlmServer.getCapturedTool("readSkill"));
    }
    
    @Test
    public void test_has_agents_md() throws IOException {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.updateConfig(aiService.getConfig().toBuilder()
                .providerType(AiProvider.OPEN_AI)
                .url(mockLlmServer.getUrl())
                .build());
        mockLlmServer.queueResponse(AiMessage.aiMessage("Pong"));
        eclipseWriteFile("AGENTS.md", "# Test Specifics");
        
        // WHEN
        aiService.setProject(project);
        aiService.getActiveAgent().setUserContextInformations(standingOrders.build());
        aiService.getActiveAgent().call("Ping", null);
        
        // THEN
        assertHasMessageWith(standingOrders.build(), "# Test Specifics");
        
        // AND
        assertNotNull(mockLlmServer.getCapturedTool("readSkill"));
        var userMessages = mockLlmServer.getCapturedMessages().stream()
                .filter(m -> m instanceof UserMessage)
                .map(m -> ((UserMessage)m)).toList();
        
        assertHasUserMessageWith(userMessages, "# Test Specifics");
    }

    @Test
    public void testHandoffStandingOrder() {
        // GIVEN: plan agent with a saved plan
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.setProject(project);
        aiService.setActiveAgent(AiPlanAgent.NAME);
        aiService.getToolService().getTool(PlanTool.class).get().planSave("# Test Plan");

        // WHEN: handoff occurs
        boolean handedOff = aiService.onHandoff();

        // THEN: handoff succeeded and agent switched
        assertTrue("handoff should succeed with a plan", handedOff);
        assertEquals(AiDevAgent.NAME, aiService.getActiveAgent().getName());

        // AND: first get() returns the handoff standing order
        var orders = aiService.get();
        assertEquals(2, orders.size());
        assertContains(orders.get(0), "Handover from ");

        // AND: second get() contains still the reference to the plan
        var orders2 = aiService.get();
        assertContains(orders2.getFirst(), "peon-plan/overview.md");
    }
    
    @Test
    public void test_update_token_limit() throws IOException {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var config = aiService.getConfig().toBuilder()
                .providerType(AiProvider.OPEN_AI)
                .url(mockLlmServer.getUrl()).build();
        aiService.updateConfig(config);

        // WHEN
        aiService.updateConfig(config.toBuilder().autoCompactAfter(4000).build());

        // THEN
        assertEquals(4000, aiService.getConfig().getAutoCompactAfter());
    }

    @Test
    public void test_plan_handling() {
        // GIVEN
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.setProject(project);
        aiService.getAgent(AiDevAgent.NAME).get().getMemory().add(UserMessage.from("FOO BAR"));
        aiService.setActiveAgent(AiPlanAgent.NAME);
        aiService.getToolService().getTool(PlanTool.class).get().planSave("Das ist ein toller plan!");
        
        // WHEN
        boolean handOff = aiService.onHandoff();
        
        // THEN
        assertTrue("We have a plan - handoff should work.", handOff);
        // AND
        assertEquals(AiDevAgent.NAME, aiService.getActiveAgent().getName());
        // AND
        assertContains(aiService.getActiveAgent().getMemory().getLastOf(UserMessage.class).singleText(),
                "Das ist ein toller plan!");
        assertContains(aiService.getActiveAgent().getMemory().getLastOf(UserMessage.class).singleText(),
                "Handover");
        assertContains(aiService.getActiveAgent().getMemory().getLastOf(UserMessage.class).singleText(),
                AiPlanAgent.NAME);
    }
    
    @Test
    public void test_AiScaffoldAgent_tools() {
        // GIVEN
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var config = aiService.getConfig().toBuilder()
                .providerType(AiProvider.OPEN_AI)
                .url(mockLlmServer.getUrl()).build();
        aiService.updateConfig(config);
        aiService.setActiveAgent(AiScaffoldAgent.NAME);
        
        // WHEN
        aiService.getActiveAgent().setUserContextInformations(standingOrders.build());
        aiService.getActiveAgent().call("hello", null);
        
        // THEN
        assertTrue(standingOrders.build().size() > 1);
        var msg = mockLlmServer.getLastRequestBody();
        assertContains(msg, "- memoryAdd:");
        // AND
        var um = aiService.getActiveAgent().getMemory().getLastOf(UserMessage.class);
        assertTrue(um.contents().size() > 2);
        assertHasUserMessageWith(Arrays.asList(um), "- memoryAdd:");
    }
    
    @Test
    public void test_dedup_messages() {
        // GIVEN
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var config = aiService.getConfig().toBuilder()
                .providerType(AiProvider.OPEN_AI)
                .url(mockLlmServer.getUrl()).build();

        aiService.updateConfig(config);
        aiService.setActiveAgent(AiDevAgent.NAME);
        aiService.getActiveAgent().getMemory().add(UserMessage.from("Text 1"));
        aiService.getActiveAgent().getMemory().add(UserMessage.from("Text 2"));

        // WHEN
        aiService.getActiveAgent().setUserContextInformations(Arrays.asList("Text 1", "Text 2", "Text 3", "Text 3", "Unique"));
        aiService.getActiveAgent().call("Text 1", null);
        
        // THEN
        var captured = mockLlmServer.getCapturedMessages();
        var lastUserMsg = captured.stream()
                .filter(m -> m instanceof UserMessage)
                .map(m -> (UserMessage)m)
                .reduce((a, b) -> b)
                .orElseThrow();
        
        var textContents = lastUserMsg.contents().stream()
                .filter(c -> c instanceof dev.langchain4j.data.message.TextContent)
                .map(c -> ((dev.langchain4j.data.message.TextContent)c).text())
                .toList();
        
        // Text 1 appears twice: once from userContextInformations (not filtered because memory
        // check happens before the new message is added) and once from the call message
        assertEquals("Text 1 should appear twice (context + call)", 2, countText(textContents, "Text 1"));
        
        // Text 2 appears once from userContextInformations
        assertEquals("Text 2 should appear once", 1, countText(textContents, "Text 2"));
        
        // Text 3 was dedupped: only one occurrence despite being in userContextInformations twice
        assertEquals("Text 3 should appear once (dedupped)", 1, countText(textContents, "Text 3"));
        
        // Unique appears once
        assertEquals("Unique should appear once", 1, countText(textContents, "Unique"));
    }
    
    /** Jon must be the first entry in the agent dropdown. */
    @Test
    public void test_po_is_first_agent() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        assertEquals(AiPoAgent.NAME, aiService.getAgents().get(0).getName());
    }

    /**
     * Regression: opening Jon first used to yield "No model configured" because AiPoAgent inherited the
     * no-op setAgentModelName default. Jon now uses the plan model slot and defaults to the dev/main model.
     */
    @Test
    public void test_po_model_uses_plan_slot_and_defaults_to_dev_model() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        // GIVEN a dev/default model, no plan model, Jon active
        aiService.updateConfig(aiService.getConfig().toBuilder().model("dev-model").planModel(null).build());
        aiService.setActiveAgent(AiPoAgent.NAME);

        // THEN Jon defaults to the dev/main model (never empty -> no "No model configured")
        assertEquals("dev-model", aiService.getActiveModel());

        // WHEN a model is selected while Jon is active (used to be dropped for PO)
        assertTrue(aiService.getActiveAgent().setAgentModelName("po-model"));

        // THEN it lands in the plan slot and is what Jon reports and runs on
        assertEquals("po-model", aiService.getActiveModel());
        assertEquals("po-model", aiService.getConfig().getPlanModel());
    }

    /**
     * The docs/index.md seed is offered only for Jon's FIRST user message (empty memory) and never
     * mutates memory itself — the view folds it into the first message as a one-time standing order.
     * Activation / project-set must not seed. Regression for the seeding-timing change.
     */
    @Test
    public void test_po_docs_index_seed_only_for_first_message() throws IOException {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        eclipseWriteFile("docs/index.md", "# Docs Index" + System.lineSeparator() + "- feature-x");
        aiService.setProject(project);
        aiService.setActiveAgent(AiPoAgent.NAME);
        aiService.getActiveAgent().getMemory().clear();

        // Activating Jon / (re)setting the project must NOT seed memory
        aiService.setProject(project);
        assertEquals(0, aiService.getActiveAgent().getMemory().size());

        // WHEN the first message is about to be sent: the seed text is offered (view attaches it)
        var seed = aiService.docsIndexSeedForFirstMessage();
        assertNotNull(seed);
        assertContains(seed, "feature-x");
        // reading the seed does not mutate memory — the fold happens in call() via the standing order
        assertEquals(0, aiService.getActiveAgent().getMemory().size());

        // AND once Jon has state, no more seed is offered (empty-memory guard)
        aiService.getActiveAgent().getMemory().add(UserMessage.from("hi"));
        assertNull(aiService.docsIndexSeedForFirstMessage());
    }

    /**
     * "Die Info": on an empty workspace with no docs/index.md, Jon greets the user with a one-time chat
     * tutorial. It is the clean complement of the docs-index seed — never shown once an index exists (the
     * seed handles that) and never once Jon has state. Regression for the getPoTutorial trigger.
     */
    @Test
    public void test_po_tutorial_only_without_docs_index() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        eclipseDeleteResource("docs/index.md");
        aiService.setProject(project);
        aiService.setActiveAgent(AiPoAgent.NAME);
        aiService.getActiveAgent().getMemory().clear();

        // no index + empty memory -> the intro is offered
        var intro = aiService.getPoTutorial();
        assertNotNull(intro);
        assertContains(intro, "Jon");

        // once Jon has state, no more intro (empty-memory guard)
        aiService.getActiveAgent().getMemory().add(UserMessage.from("hi"));
        assertNull(aiService.getPoTutorial());

        // and when an index exists, the seed owns onboarding -> no intro even with empty memory
        aiService.getActiveAgent().getMemory().clear();
        eclipseWriteFile("docs/index.md", "# Docs Index");
        assertNull(aiService.getPoTutorial());
    }

    /** Jon carries his delegate tools (talk/plan/ask/build) plus his own search sub-agent. */
    @Test
    public void test_po_has_delegate_tools() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.setActiveAgent(AiPoAgent.NAME);

        var names = aiService.getActiveAgent().getToolService().toolSpecifications().stream()
                .map(t -> t.name()).toList();
        assertTrue("talkPlan expected", names.contains("talkPlan"));
        assertTrue("planWithPlanAgent expected", names.contains("planWithPlanAgent"));
        assertTrue("askDev expected", names.contains("askDev"));
        assertTrue("buildWithAgent expected", names.contains("buildWithAgent"));
        assertTrue("searchAgent expected", names.contains("searchAgent"));
    }

    /**
     * Jon owns the shared memory (write) — he steers all agents through it — plus read-only plan access
     * (hasPlan/planRead). He never writes plans himself (that is delegated to his Peon-Plan slave).
     */
    @Test
    public void test_po_has_memory_write_and_readonly_plan() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.setActiveAgent(AiPoAgent.NAME);

        var names = aiService.getActiveAgent().getToolService().toolSpecifications().stream()
                .map(t -> t.name()).toList();
        assertTrue("memoryAdd (write) expected", names.contains("memoryAdd"));
        assertTrue("planRead expected", names.contains("planRead"));
        assertTrue("hasPlan expected", names.contains("hasPlan"));
        // Jon delegates planning — he must not be able to write the plan himself
        assertFalse("planSave must not be Jon's tool", names.contains("planSave"));
    }

    /** Slaves may READ the shared memory (injected) but must NOT be able to write it. */
    @Test
    public void test_po_slaves_cannot_write_memory() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.setActiveAgent(AiPoAgent.NAME);
        var jon = aiService.getActiveAgent();

        var delegate = jon.getToolService().getTool(JonDelegateTool.class).orElseThrow();
        var memoryWrite = delegate.getPlanSlave().getToolService().getExecutor("memoryAdd");
        assertNotNull("memoryAdd executor is present in the shared tool set", memoryWrite);

        assertFalse("Plan slave cannot write memory", delegate.getPlanSlave().isToolActive(memoryWrite));
        assertFalse("Dev slave cannot write memory", delegate.getDevSlave().isToolActive(memoryWrite));
    }

    /**
     * ADR-0024: Jon keeps a durable (persisted) memory, but his Plan/Dev slaves are RAM-only and are
     * distinct instances from the user-selectable Peon-Plan / Peon-Dev agents.
     */
    @Test
    public void test_po_slaves_are_ram_only_jon_is_durable() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.setActiveAgent(AiPoAgent.NAME);
        var jon = aiService.getActiveAgent();

        assertTrue("Jon persists his state", jon.getMemory().isPersistent());

        var delegate = jon.getToolService().getTool(JonDelegateTool.class).orElseThrow();
        assertFalse("Plan slave is RAM-only", delegate.getPlanSlave().getMemory().isPersistent());
        assertFalse("Dev slave is RAM-only", delegate.getDevSlave().getMemory().isPersistent());

        // distinct from the user-selectable standalone agents
        assertNotSame(aiService.getAgent(AiPlanAgent.NAME).get(), delegate.getPlanSlave());
        assertNotSame(aiService.getAgent(AiDevAgent.NAME).get(), delegate.getDevSlave());
    }

    /**
     * Inc 2 (docs/sklaven-kontext-plan.md): the static context (date/OS + file-access rules) must reach
     * Jon's RAM slaves too — they are not in agentService, so setStaticContext applies it directly.
     */
    @Test
    public void test_static_context_reaches_jons_slaves() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.setActiveAgent(AiPoAgent.NAME);
        var delegate = aiService.getActiveAgent().getToolService().getTool(JonDelegateTool.class).orElseThrow();

        var ctx = dev.langchain4j.data.message.SystemMessage.from("Today is 2026-08-06; prefer eclipse* over disk*.");
        aiService.setStaticContext(List.of(ctx));

        assertTrue("Plan slave got the static context", delegate.getPlanSlave().getStaticContext().contains(ctx));
        assertTrue("Dev slave got the static context", delegate.getDevSlave().getStaticContext().contains(ctx));
    }

    private int countText(List<String> texts, String text) {
        return (int) texts.stream().filter(t -> text.equals(t)).count();
    }

    // --- Header status widget MVP (agenten-status-im-header-mvp-plan.md, ADR-0025) -------------

    /** GIVEN Jon is active THEN getStatusAgents() is his team: Da Boss first, then Da Thinka, Da Mek. */
    @Test
    public void test_status_agents_are_jons_team_when_po_active() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.setActiveAgent(AiPoAgent.NAME);

        var uiNames = aiService.getStatusAgents().stream().map(n -> n.uiName()).toList();
        assertEquals(List.of("Da Boss", "Da Thinka", "Da Mek"), uiNames);
    }

    /** GIVEN any other agent is active THEN the status widget shows nothing (empty list). */
    @Test
    public void test_status_agents_empty_for_other_agents() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.setActiveAgent(AiDevAgent.NAME);
        assertTrue("only Jon shows a status team", aiService.getStatusAgents().isEmpty());
    }

    /**
     * The status list follows the active agent with no stale state: each {@code getStatusAgents()}
     * re-reads the live IST, so switching PO → Dev flips team → empty on the very next call (the old
     * roster's cache-freeness regression, now on the pull choke-point).
     */
    @Test
    public void test_status_agents_follow_switch_no_stale_state() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.setActiveAgent(AiPoAgent.NAME);
        assertFalse("Jon shows his team", aiService.getStatusAgents().isEmpty());

        aiService.setActiveAgent(AiDevAgent.NAME);
        assertTrue("switching away clears it immediately", aiService.getStatusAgents().isEmpty());
    }
}
