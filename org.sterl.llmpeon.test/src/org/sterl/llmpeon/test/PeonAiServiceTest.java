package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;
import org.sterl.llmpeon.StandingOrdersBuilder;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.parts.PeonAiService;
import org.sterl.llmpeon.parts.tools.PlanTool;
import org.sterl.llmpeon.tool.tools.CompactSessionTool;
import org.sterl.llmpeon.tool.tools.DiskFileReadTool;
import org.sterl.llmpeon.tool.tools.DiskFileWriteTool;
import org.sterl.llmpeon.tool.tools.DiskGrepTool;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;

public class PeonAiServiceTest extends AbstractTest {

    PeonAiService aiService = new PeonAiService(null, null, null, null);
    
    private final StandingOrdersBuilder standingOrders = new StandingOrdersBuilder()
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
        assertEquals("Ping", ((UserMessage)msg.get(0)).singleText());
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
        
        aiService.getAgentsMdService().load(project);
        
        // WHEN
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
    
    // TODO add tests concerning the message build -- check if it was properly constructed.

}
