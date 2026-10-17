package org.sterl.llmpeon.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

import java.util.Map;

import dev.langchain4j.mcp.client.McpClient;

import org.junit.jupiter.api.Test;

/**
 * {@code instructionsOf} exposes the server instructions captured from the MCP initialize
 * result (langchain4j McpClient contract: available after {@code listTools()}).
 */
class McpServiceInstructionsTest {

    @Test
    void givenConnectedClientWithInstructions_whenInstructionsOf_thenValueReturned() throws Exception {
        // GIVEN a fake client registered under its server name
        var service = new McpService(List.of());
        inject(service, "fake", fakeClient("CG-HELLO"));

        // WHEN the instructions are requested
        Optional<String> instructions = service.instructionsOf("fake");

        // THEN the captured value is returned
        assertThat(instructions).contains("CG-HELLO");
    }

    @Test
    void givenUnknownServer_whenInstructionsOf_thenEmpty() {
        // GIVEN a service without that server
        var service = new McpService(List.of());

        // WHEN the instructions are requested for an unknown name
        Optional<String> instructions = service.instructionsOf("ghost");

        // THEN empty — no exception, no default text
        assertThat(instructions).isEmpty();
    }

    /** JDK dynamic proxy implementing the McpClient interface — only {@code instructions()} matters here. */
    private static McpClient fakeClient(String instructions) {
        return (McpClient) Proxy.newProxyInstance(
                McpClient.class.getClassLoader(),
                new Class<?>[] { McpClient.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "instructions" -> instructions;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "fake-mcp-client";
                    default -> null;
                });
    }

    @SuppressWarnings("unchecked")
    private static void inject(McpService service, String name, McpClient client) throws Exception {
        var clients = McpService.class.getDeclaredField("clients");
        clients.setAccessible(true);
        ((List<McpClient>) clients.get(service)).add(client);
        var byName = McpService.class.getDeclaredField("clientsByName");
        byName.setAccessible(true);
        ((Map<String, McpClient>) byName.get(service)).put(name, client);
    }
}
