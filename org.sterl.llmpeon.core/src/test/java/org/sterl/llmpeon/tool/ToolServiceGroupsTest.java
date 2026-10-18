package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.mcp.McpService;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.mockito.Mockito;

/**
 * ADR-0069: ToolService as adopt-only MCP registry — groups are ADOPTED (already-connected
 * McpServices handed over by their owner), never created or closed here: disconnectMcp(group)
 * only detaches THIS registry, the owner (connection service) closes the service. One service
 * adopted into several registries stays one process (Paul-(b)/(c)).
 */
class ToolServiceGroupsTest {

    private static final String USER = "user";
    private static final String CODEGRAPH = "codegraph";

    @Test
    void givenUserAndCodegraphAdopted_whenToolNames_thenUnionDeterministic() {
        // GIVEN both groups adopted (group names sort: codegraph < user)
        var service = new ToolService(false);
        service.adoptMcpGroup(USER, new FakeMcpService(specs(USER), USER));
        service.adoptMcpGroup(CODEGRAPH, new FakeMcpService(specs(CODEGRAPH), CODEGRAPH));

        // WHEN the tool names are read
        var names = service.mcpToolNames();

        // THEN the union is deterministic: sorted group names, then spec order
        assertThat(names).containsExactly("codegraph_tool1", "codegraph_tool2", "user_tool1", "user_tool2");
    }

    @Test
    void givenBothGroupsAdopted_whenDisconnectUserGroup_thenOnlyUserDetachedNotClosed() {
        // GIVEN both groups adopted
        var service = new ToolService(false);
        var userFake = new FakeMcpService(specs(USER), USER);
        var codegraphFake = new FakeMcpService(specs(CODEGRAPH), CODEGRAPH);
        service.adoptMcpGroup(USER, userFake);
        service.adoptMcpGroup(CODEGRAPH, codegraphFake);

        // WHEN the no-arg disconnect runs (the user MCP toggle-off path)
        service.disconnectMcp();

        // THEN only the user group is detached — the adopted service is NOT closed (owner
        // closes, ADR-0069), the codegraph group stays (R-CG-1)
        assertThat(service.mcpToolNames()).containsExactly("codegraph_tool1", "codegraph_tool2");
        assertThat(userFake.disconnectCalls).isZero();
        assertThat(codegraphFake.disconnectCalls).isZero();
    }

    @Test
    void givenBothGroupsAdopted_whenDisconnectCodegraphGroup_thenOnlyCodegraphDetached() {
        // GIVEN both groups adopted
        var service = new ToolService(false);
        var userFake = new FakeMcpService(specs(USER), USER);
        var codegraphFake = new FakeMcpService(specs(CODEGRAPH), CODEGRAPH);
        service.adoptMcpGroup(USER, userFake);
        service.adoptMcpGroup(CODEGRAPH, codegraphFake);

        // WHEN the codegraph group is detached by name
        service.disconnectMcp(CODEGRAPH);

        // THEN only the codegraph group is gone — neither adopted service is closed
        assertThat(service.mcpToolNames()).containsExactly("user_tool1", "user_tool2");
        assertThat(userFake.disconnectCalls).isZero();
        assertThat(codegraphFake.disconnectCalls).isZero();
    }

    @Test
    void givenGroupAdopted_whenReadoptedWithOtherService_thenNoDuplicatesOldServiceNotClosed() {
        // GIVEN a user group adopted with the first service
        var service = new ToolService(false);
        var oldFake = new FakeMcpService(specs(USER), USER);
        service.adoptMcpGroup(USER, oldFake);

        // WHEN the same group is re-adopted with a different (already-connected) service
        var newFake = new FakeMcpService(specs(USER), USER);
        service.adoptMcpGroup(USER, newFake);

        // THEN the union holds each tool exactly once (replace, not append) and the old
        // service is NOT closed by the registry (ADR-0069 re-adopt)
        assertThat(service.mcpToolNames()).containsExactly("user_tool1", "user_tool2");
        assertThat(oldFake.disconnectCalls).isZero();
    }

    @Test
    void givenBothGroupsAdopted_whenExecutingTool_thenRoutedToAdoptedGroup() {
        // GIVEN both groups adopted
        var service = new ToolService(false);
        service.adoptMcpGroup(USER, new FakeMcpService(specs(USER), USER));
        service.adoptMcpGroup(CODEGRAPH, new FakeMcpService(specs(CODEGRAPH), CODEGRAPH));
        var req = loopRequest();

        // WHEN both tool names are executed
        var userResult = service.execute(request("user_tool1"), req);
        var codegraphResult = service.execute(request("codegraph_tool1"), req);

        // THEN each request reached exactly its adopted service
        assertThat(userResult.text()).isEqualTo("user_result:user_tool1");
        assertThat(codegraphResult.text()).isEqualTo("codegraph_result:codegraph_tool1");
    }

    @Test
    void givenAdoptedGroupInTwoRegistries_whenDisconnectMcpOnOne_thenServiceNotClosedOtherRegistryStillExecutes() {
        // GIVEN the same connected service adopted as one group into TWO registries
        var fake = new FakeMcpService(specs(CODEGRAPH), CODEGRAPH);
        var registry1 = new ToolService(false);
        var registry2 = new ToolService(false);
        registry1.adoptMcpGroup(CODEGRAPH, fake);
        registry2.adoptMcpGroup(CODEGRAPH, fake);

        // WHEN registry 1 detaches the group (the owner's detach loop runs per registry)
        registry1.disconnectMcp(CODEGRAPH);

        // THEN the service is NOT closed by the detach, registry 2 still lists its tools and
        // executes through the same service
        assertThat(fake.disconnectCalls).isZero();
        assertThat(registry1.mcpToolNames()).isEmpty();
        assertThat(registry2.mcpToolNames()).containsExactly("codegraph_tool1", "codegraph_tool2");
        var result = registry2.execute(request("codegraph_tool1"), loopRequest());
        assertThat(result.text()).isEqualTo("codegraph_result:codegraph_tool1");
        assertThat(fake.executed).containsExactly("codegraph_tool1");
    }

    @Test
    void givenSameServiceAdoptedInTwoRegistries_whenExecutingFromBoth_thenOneServiceReceivesBothCalls() {
        // GIVEN one connected service adopted into two registries
        var fake = new FakeMcpService(specs(USER), USER);
        var registry1 = new ToolService(false);
        var registry2 = new ToolService(false);
        registry1.adoptMcpGroup(USER, fake);
        registry2.adoptMcpGroup(USER, fake);

        // WHEN a tool is executed through each registry
        registry1.execute(request("user_tool1"), loopRequest());
        registry2.execute(request("user_tool2"), loopRequest());

        // THEN both calls landed on the SAME service instance (one process, two registries)
        assertThat(fake.executed).containsExactly("user_tool1", "user_tool2");
    }

    // R-RS-3
    @Test
    void givenMcpGroupAdopted_whenExecutingTool_thenCompletionLineDisclosesResultChars() {
        // GIVEN an MCP group adopted whose tool returns a 3-char string
        var service = new ToolService(false);
        service.adoptMcpGroup(USER, new FixedMcpService("abc"));
        var tools = new ArrayList<String>();
        var req = loopRequest(monitorCapturing(tools));

        // WHEN the MCP tool is executed
        var result = service.execute(request("mcp_tool"), req);

        // THEN the result is untouched and the monitor got the start line plus the
        // completion line with the exact size
        assertThat(result.text()).isEqualTo("abc");
        assertThat(tools).containsExactly("Running MCP: mcp_tool - {}", "MCP mcp_tool done. (3 chars)");
    }

    // R-RS-3
    @Test
    void givenMcpResultNullOrEmpty_whenExecutingTool_thenCompletionLineDisclosesZeroChars() {
        // GIVEN an adopted MCP tool returning null, later re-adopted with an empty result
        var service = new ToolService(false);
        service.adoptMcpGroup(USER, new FixedMcpService(null));
        var tools = new ArrayList<String>();
        var req = loopRequest(monitorCapturing(tools));

        // WHEN the tool is executed in both cases
        service.execute(request("mcp_tool"), req);
        service.adoptMcpGroup(USER, new FixedMcpService(""));
        service.execute(request("mcp_tool"), req);

        // THEN the completion line is an honest zero — never omitted
        assertThat(tools).containsExactly(
                "Running MCP: mcp_tool - {}", "MCP mcp_tool done. (0 chars)",
                "Running MCP: mcp_tool - {}", "MCP mcp_tool done. (0 chars)");
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
        return loopRequest(AiMonitor.NULL_MONITOR);
    }

    private static ToolLoopRequest loopRequest(AiMonitor monitor) {
        return ToolLoopRequest.builder()
                .memory(new ThreadSafeMemory())
                .chatModel(new ConfiguredChatModel(LlmConfig.newOpenAi("foo"), Mockito.mock(StreamingChatModel.class)))
                .monitor(monitor)
                .build();
    }

    private static AiMonitor monitorCapturing(List<String> tools) {
        return new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public void onTool(String message) { tools.add(message); }
        };
    }

    /** Canned McpService: no real transport, counts connect/disconnect calls, canned specs + routing. */
    private static class FakeMcpService extends McpService {
        final List<ToolSpecification> canned;
        final String prefix;
        int connectCalls;
        int disconnectCalls;
        final List<String> executed = new java.util.ArrayList<>();

        FakeMcpService(List<ToolSpecification> canned, String prefix) {
            super(List.of());
            this.canned = canned;
            this.prefix = prefix;
        }

        @Override
        public void connect() {
            connectCalls++;
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

    /** Canned single-tool service with a fixed (possibly null) result — R-RS-3 asserts on its exact size. */
    private static class FixedMcpService extends McpService {
        private final @Nullable String result;

        FixedMcpService(@Nullable String result) {
            super(List.of());
            this.result = result;
        }

        @Override
        public List<ToolSpecification> getToolSpecifications() {
            return List.of(ToolSpecification.builder().name("mcp_tool").description("mcp_tool").build());
        }

        @Override
        public boolean hasTool(String toolName) {
            return "mcp_tool".equals(toolName);
        }

        @Override
        public String executeTool(ToolExecutionRequest request) {
            return result;
        }
    }
}
