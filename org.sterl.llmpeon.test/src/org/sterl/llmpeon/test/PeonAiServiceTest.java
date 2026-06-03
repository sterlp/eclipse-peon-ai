package org.sterl.llmpeon.test;

import static org.junit.Assume.assumeTrue;

import org.junit.Test;
import org.sterl.llmpeon.parts.PeonAiService;
import org.sterl.llmpeon.tool.tools.CompactSessionTool;
import org.sterl.llmpeon.tool.tools.DiskFileReadTool;
import org.sterl.llmpeon.tool.tools.DiskFileWriteTool;
import org.sterl.llmpeon.tool.tools.DiskGrepTool;

public class PeonAiServiceTest  extends AbstractTest {

    PeonAiService aiService = new PeonAiService(null, null, null);
    
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
    
    // TODO add tests concerning the message build -- check if it was properly constructed.

}
