package org.sterl.llmpeon.test;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;
import org.sterl.llmpeon.StandingOrdersBuilder;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.parts.PeonAiService;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.tool.tools.CompactSessionTool;
import org.sterl.llmpeon.tool.tools.DiskFileReadTool;
import org.sterl.llmpeon.tool.tools.DiskFileWriteTool;
import org.sterl.llmpeon.tool.tools.DiskGrepTool;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;

public class PeonAiServiceTest  extends AbstractTest {

    PeonAiService aiService = new PeonAiService(null, null, null);
    
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
        aiService.getDeveloperService().call("Ping", null);
        
        // THEN
        var msg = aiService.getDeveloperService().getMessages();
        assertEquals(ChatMessageUtil.toString(msg.get(0)), "Ping");
        assertEquals(ChatMessageUtil.toString(msg.get(1)), "Pong");
    }
    
    @Test
    public void test_has_read_skill_tool() throws IOException {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.updateConfig(aiService.getConfig().toBuilder()
                .providerType(AiProvider.OPEN_AI)
                .url(mockLlmServer.getUrl()).build());
        mockLlmServer.queueResponse(AiMessage.aiMessage("Pong"));
        
        // WHEN
        aiService.getDeveloperService().call("Ping", null);
        
        // THEN
        assertNotNull(mockLlmServer.getCapturedTool("readSkill"));
    }
    
    @Test
    public void test_has_agents_md() throws IOException {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.updateConfig(aiService.getConfig().toBuilder()
                .providerType(AiProvider.OPEN_AI)
                .url(mockLlmServer.getUrl()).build());
        mockLlmServer.queueResponse(AiMessage.aiMessage("Pong"));
        
        aiService.getAgentsMdService().load(project);
        
        // WHEN
        aiService.getDeveloperService().setUserContextInformations(standingOrders.build());
        aiService.getDeveloperService().call("Ping", null);
        
        // THEN
        assertNotNull(mockLlmServer.getCapturedTool("readSkill"));
        var userMessages = mockLlmServer.getCapturedMessages().stream()
                .filter(m -> m instanceof UserMessage)
                .map(m -> ((UserMessage)m)).toList();
        
        assertContains(ChatMessageUtil.toString(userMessages.get(0)), "Test Specifics");
    }
    
    // TODO add tests concerning the message build -- check if it was properly constructed.

}
