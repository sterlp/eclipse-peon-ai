package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.Test;
import org.sterl.llmpeon.StandingOrdersBuilder;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.parts.PeonAiService;
import org.sterl.llmpeon.parts.tools.PlanTool;
import org.sterl.llmpeon.scaffold.AiScaffoldAgent;
import org.sterl.llmpeon.tool.tools.CompactSessionTool;
import org.sterl.llmpeon.tool.tools.DiskFileReadTool;
import org.sterl.llmpeon.tool.tools.DiskFileWriteTool;
import org.sterl.llmpeon.tool.tools.DiskGrepTool;

import dev.langchain4j.data.message.AiMessage;
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
    public void test_dedup_messages() throws IOException {
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
        var msg = mockLlmServer.getLastRequestBody();
        // AND
        var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(msg);
        var messages = root.path("messages");
        var lastUserMsg = messages.get(messages.size() - 1);
        var contentArray = lastUserMsg.path("content");
        
        // Text 1 appears twice: once from userContextInformations (not filtered because memory
        // check happens before the new message is added) and once from the call message
        var text1Count = countText(contentArray, "Text 1");
        assertEquals("Text 1 should appear twice (context + call)", 2, text1Count);
        
        // Text 2 appears once from userContextInformations
        var text2Count = countText(contentArray, "Text 2");
        assertEquals("Text 2 should appear once", 1, text2Count);
        
        // Text 3 was dedupped: only one occurrence despite being in userContextInformations twice
        var text3Count = countText(contentArray, "Text 3");
        assertEquals("Text 3 should appear once (dedupped)", 1, text3Count);
        
        // Unique appears once
        var uniqueCount = countText(contentArray, "Unique");
        assertEquals("Unique should appear once", 1, uniqueCount);
    }
    
    private int countText(com.fasterxml.jackson.databind.JsonNode contentArray, String text) {
        int count = 0;
        for (var item : contentArray) {
            if (text.equals(item.path("text").asText())) {
                count++;
            }
        }
        return count;
    }
    
    // TODO add tests concerning the message build -- check if it was properly constructed.

}
