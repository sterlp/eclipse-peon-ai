package org.sterl.llmpeon.mcp;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.mcp.McpServerConfig.McpTransportType;

// run: npx @arabold/docs-mcp-server
class McpServiceTest {

    @Test
    @Tag("integration")
    void testMCP() {
        McpService.testConnect(new McpServerConfig("test", McpTransportType.HTTP, "http://127.0.0.1:6280/mcp"));
    }

    @Test
    @Tag("integration")
    void testMCP_SEE() {
        McpService.testConnect(new McpServerConfig("test", McpTransportType.HTTP_SSE, "http://127.0.0.1:6280/sse"));
    }

}
