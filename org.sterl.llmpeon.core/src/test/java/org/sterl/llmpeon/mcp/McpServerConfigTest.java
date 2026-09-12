package org.sterl.llmpeon.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.mcp.McpServerConfig.McpTransportType;

/**
 * R-MCP2: protocol-version semantics. An empty protocolVersion stays empty (Auto-Detect);
 * there is no silent default version that langchain4j would treat as "a different version".
 */
class McpServerConfigTest {

    @Test
    void givenEmptyProtocolVersion_whenConstructed_thenStaysEmpty() {
        // GIVEN a config with an empty protocolVersion
        var config = new McpServerConfig("ctx", McpTransportType.HTTP, "https://mcp.context7.com/mcp",
                "", "", "", "", "");

        // WHEN constructed
        // (record compact constructor normalizes)

        // THEN the value stays empty — empty means Auto-Detect, not a hard-coded default
        assertThat(config.protocolVersion()).isEmpty();
    }

    @Test
    void givenNullProtocolVersion_whenConstructed_thenNormalizedToEmpty() {
        // GIVEN a null protocolVersion
        var config = new McpServerConfig("ctx", McpTransportType.HTTP, "https://mcp.context7.com/mcp",
                "", null, "", "", "");

        // THEN null is normalized to empty (Auto-Detect), never to a default version
        assertThat(config.protocolVersion()).isEmpty();
    }

    @Test
    void givenExplicitProtocolVersion_whenConstructed_thenValuePreserved() {
        // GIVEN a config with an explicit protocol version
        var config = new McpServerConfig("ctx", McpTransportType.HTTP, "https://mcp.context7.com/mcp",
                "", "2026-07-28", "", "", "");

        // THEN the value is preserved untouched (normalization only fires on empty/null)
        assertThat(config.protocolVersion()).isEqualTo("2026-07-28");
    }

    @Test
    void givenThreeArgCtor_whenConstructed_thenProtocolVersionEmpty() {
        // GIVEN the convenience 3-arg constructor (no protocol version supplied)
        var config = new McpServerConfig("ctx", McpTransportType.HTTP, "https://mcp.context7.com/mcp");

        // THEN it defaults to empty = Auto-Detect, not a hard-coded version
        assertThat(config.protocolVersion()).isEmpty();
    }
}
