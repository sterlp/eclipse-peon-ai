package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.mcp.McpServerConfig;
import org.sterl.llmpeon.mcp.McpService;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.mockito.Mockito;

/**
 * ADR-0068: MCP groups in ToolService — the user group keeps its exact semantics, built-in
 * groups (e.g. "codegraph") are failure-isolated: a group connect failure or a user-group
 * disconnect never touches the other groups.
 */
class ToolServiceGroupsTest {

    private static final String USER = "user";
    private static final String CODEGRAPH = "codegraph";

    private TestToolService serviceWith(String... groupNames) {
        var service = new TestToolService();
        for (var name : groupNames) {
            service.fakes.put(name, new FakeMcpService(specs(name), null, name));
        }
        return service;
    }

    @Test
    void givenUserAndCodegraphConnected_whenToolNames_thenUnionDeterministic() {
        // GIVEN both groups connected (group names sort: codegraph < user)
        var service = serviceWith(USER, CODEGRAPH);
        service.connectMcp(List.of(server(USER)));
        service.connectMcp(CODEGRAPH, server(CODEGRAPH));

        // WHEN the tool names are read
        var names = service.mcpToolNames();

        // THEN the union is deterministic: sorted group names, then spec order
        assertThat(names).containsExactly("codegraph_tool1", "codegraph_tool2", "user_tool1", "user_tool2");
    }

    @Test
    void givenCodegraphConnectFails_whenConnected_thenUserGroupIntact() {
        // GIVEN a healthy user group and a codegroup whose connect throws
        var service = serviceWith(USER, CODEGRAPH);
        service.connectMcp(List.of(server(USER)));
        var codegraphFake = service.fakes.get(CODEGRAPH);
        codegraphFake.failure = new RuntimeException("spawn failed");

        // WHEN the codegraph group connect fails
        assertThatThrownBy(() -> service.connectMcp(CODEGRAPH, server(CODEGRAPH)))
                .isInstanceOf(RuntimeException.class);

        // THEN the user group is intact — specs present, no user disconnect happened (R-CG-3)
        var userFake = service.fakes.get(USER);
        assertThat(service.mcpToolNames()).containsExactly("user_tool1", "user_tool2");
        assertThat(userFake.disconnectCalls).isZero();
        assertThat(codegraphFake.connectCalls).isEqualTo(1);
    }

    @Test
    void givenBothGroupsConnected_whenDisconnectMcpNoArg_thenOnlyUserGroupRemoved() {
        // GIVEN both groups connected
        var service = serviceWith(USER, CODEGRAPH);
        service.connectMcp(List.of(server(USER)));
        service.connectMcp(CODEGRAPH, server(CODEGRAPH));

        // WHEN the no-arg disconnect runs (the user MCP toggle-off path)
        service.disconnectMcp();

        // THEN only the user group is gone — the codegraph group stays connected (R-CG-1)
        assertThat(service.mcpToolNames()).containsExactly("codegraph_tool1", "codegraph_tool2");
        assertThat(service.fakes.get(USER).disconnectCalls).isEqualTo(1);
        assertThat(service.fakes.get(CODEGRAPH).disconnectCalls).isZero();
    }

    @Test
    void givenBothGroupsConnected_whenDisconnectCodegraphGroup_thenOnlyCodegraphRemoved() {
        // GIVEN both groups connected
        var service = serviceWith(USER, CODEGRAPH);
        service.connectMcp(List.of(server(USER)));
        service.connectMcp(CODEGRAPH, server(CODEGRAPH));

        // WHEN the codegraph group is disconnected by name
        service.disconnectMcp(CODEGRAPH);

        // THEN only the codegraph group is gone
        assertThat(service.mcpToolNames()).containsExactly("user_tool1", "user_tool2");
        assertThat(service.fakes.get(CODEGRAPH).disconnectCalls).isEqualTo(1);
        assertThat(service.fakes.get(USER).disconnectCalls).isZero();
    }

    @Test
    void givenReconnectSameGroup_whenConnectedAgain_thenNoDuplicatesInUnion() {
        // GIVEN a user group connected once
        var service = serviceWith(USER);
        service.connectMcp(List.of(server(USER)));

        // WHEN the same group is reconnected (config change)
        service.fakes.put(USER, new FakeMcpService(specs(USER), null, USER));
        service.connectMcp(List.of(server(USER)));

        // THEN the union holds each tool exactly once (replace, not append)
        assertThat(service.mcpToolNames()).containsExactly("user_tool1", "user_tool2");
    }

    @Test
    void givenBothGroupsConnected_whenExecutingTool_thenRoutedToOwningGroup() {
        // GIVEN both groups connected
        var service = serviceWith(USER, CODEGRAPH);
        service.connectMcp(List.of(server(USER)));
        service.connectMcp(CODEGRAPH, server(CODEGRAPH));
        var req = loopRequest();

        // WHEN both tool names are executed
        var userResult = service.execute(request("user_tool1"), req);
        var codegraphResult = service.execute(request("codegraph_tool1"), req);

        // THEN each request reached exactly its owning group
        assertThat(userResult.text()).isEqualTo("user_result:user_tool1");
        assertThat(codegraphResult.text()).isEqualTo("codegraph_result:codegraph_tool1");
    }

    private static McpServerConfig server(String name) {
        return new McpServerConfig(name, McpServerConfig.McpTransportType.HTTP,
                "https://" + name + ".example.com/mcp");
    }

    private static List<ToolSpecification> specs(String groupName) {
        return List.of(
                ToolSpecification.builder().name(groupName + "_tool1").description(groupName + "_tool1").build(),
                ToolSpecification.builder().name(groupName + "_tool2").description(groupName + "_tool2").build());
    }

    private static ToolExecutionRequest request(String name) {
        return ToolExecutionRequest.builder().id("1").name(name).arguments("{}").build();
    }

    private static ToolLoopRequest loopRequest() {
        return ToolLoopRequest.builder()
                .memory(new ThreadSafeMemory())
                .chatModel(new ConfiguredChatModel(LlmConfig.newOpenAi("foo"), Mockito.mock(StreamingChatModel.class)))
                .build();
    }

    /** ToolService with the McpService factory swapped for canned fakes (package-private seam). */
    private static class TestToolService extends ToolService {
        final Map<String, FakeMcpService> fakes = new HashMap<>();

        TestToolService() {
            super(false);
        }

        @Override
        McpService createMcpService(List<McpServerConfig> servers) {
            var fake = fakes.get(servers.get(0).name());
            if (fake == null) throw new IllegalStateException("no fake for " + servers.get(0).name());
            return fake;
        }
    }

    /** Canned McpService: no real transport, counts connect/disconnect, canned specs + routing. */
    private static class FakeMcpService extends McpService {
        final List<ToolSpecification> canned;
        final String prefix;
        RuntimeException failure;
        int connectCalls;
        int disconnectCalls;
        final List<String> executed = new java.util.ArrayList<>();

        FakeMcpService(List<ToolSpecification> canned, RuntimeException failure, String prefix) {
            super(List.of());
            this.canned = canned;
            this.failure = failure;
            this.prefix = prefix;
        }

        @Override
        public void connect() {
            connectCalls++;
            if (failure != null) throw failure;
        }

        @Override
        public List<ToolSpecification> getToolSpecifications() {
            return canned;
        }

        @Override
        public boolean hasTool(String toolName) {
            return canned.stream().anyMatch(s -> s.name().equals(toolName));
        }

        @Override
        public String executeTool(ToolExecutionRequest request) {
            executed.add(request.name());
            return prefix + "_result:" + request.name();
        }

        @Override
        public void disconnect() {
            disconnectCalls++;
        }
    }
}
