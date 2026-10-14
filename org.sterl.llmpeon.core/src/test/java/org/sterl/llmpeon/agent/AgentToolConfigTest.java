package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.llmpeon.ai.LlmConfigLoader;
import org.sterl.llmpeon.ai.MapLlmConfigStore;
import org.sterl.llmpeon.poagent.AiPoAgent;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;
import org.sterl.llmpeon.tool.tools.DiskFileReadTool;
import org.sterl.llmpeon.tool.tools.DiskFileWriteTool;
import org.sterl.llmpeon.tool.tools.ShellTool;

/**
 * R-ATC-3/4/6/7: the per-agent tool allowlist stored under {@code llm.agent.<id>.tools} restricts
 * each agent's tools — built-in via the tool filter, MCP via the name filter — read per call,
 * slot by slot, with no inheritance between slots.
 */
class AgentToolConfigTest {

    private static ToolService toolService() {
        var svc = new ToolService(false);
        svc.addTool(new ShellTool());
        svc.addTool(new DiskFileWriteTool(Path.of(System.getProperty("java.io.tmpdir"))));
        svc.addTool(new DiskFileReadTool(Path.of(System.getProperty("java.io.tmpdir"))));
        return svc;
    }

    private static ConfiguredChatModel model(Map<String, String> agentTools) {
        return new ConfiguredChatModel(LlmConfig.builder()
                .model("test")
                .url("http://localhost:0")
                .agentTools(Map.copyOf(agentTools))
                .build());
    }

    private static SmartToolExecutor executor(ToolService svc, String name) {
        return svc.getExecutors().stream()
                .filter(e -> e.getSpec().name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no executor named " + name));
    }

    // UC-ATC-1
    // UC-ATC-5
    @Test
    void devAllowlistRestrictsDevOnly() {
        // GIVEN a stored allowlist for the dev slot only
        var svc = toolService();
        var config = Map.of("dev", "eclipse");
        var dev = new AiDevAgent(model(config), svc);
        var shell = executor(svc, ShellTool.SHELL_RUN_COMMAND);
        var diskRead = executor(svc, "diskReadFile");

        // THEN dev is restricted to eclipse* (built-ins by spec, MCP by name)
        assertThat(dev.isToolActive(shell)).isFalse();
        assertThat(dev.isToolActive(diskRead)).isFalse();
        assertThat(dev.isMcpToolActive("eclipseGrepFiles")).isTrue();
        assertThat(dev.isMcpToolActive("diskReadFile")).isFalse();

        // AND the other slots are unaffected (no inheritance; class filters still apply)
        var plan = new AiPlanAgent(model(config), svc);
        var po = new AiPoAgent(model(config), svc);
        var review = new AiReviewAgent(model(config), svc);
        assertThat(plan.isToolActive(diskRead)).isTrue();
        assertThat(po.isToolActive(shell)).isTrue();
        assertThat(review.isToolActive(diskRead)).isTrue();
    }

    @Test
    void mcpNameFilterAppliesToAllTools() {
        // GIVEN a plan slot whose allowlist only matches an MCP name prefix
        var svc = toolService();
        var plan = new AiPlanAgent(model(Map.of(AgentModelConfig.PLAN, "mcp__docs")), svc);
        var shell = executor(svc, ShellTool.SHELL_RUN_COMMAND);

        // THEN the name filter governs MCP tools and the built-in filter the rest
        assertThat(plan.isMcpToolActive("mcp__docs__search_docs")).isTrue();
        assertThat(plan.isMcpToolActive("mcp__other__x")).isFalse();
        assertThat(plan.isToolActive(shell)).isFalse();
    }

    @Test
    void negationViaConfigRemovesTool() {
        // GIVEN a dev slot with a wildcard-minus-shell allowlist
        var svc = toolService();
        var dev = new AiDevAgent(model(Map.of(AgentModelConfig.DEV, "*, !shell*")), svc);
        var shell = executor(svc, ShellTool.SHELL_RUN_COMMAND);

        // THEN the negation removes the tool from built-ins AND MCP
        assertThat(dev.isToolActive(shell)).isFalse();
        assertThat(dev.isMcpToolActive("shellMcp")).isFalse();
        assertThat(dev.isMcpToolActive("mcp__docs__search_docs")).isTrue();

        // AND the class filter stays intersected: a plan wildcard still hides edit tools
        var plan = new AiPlanAgent(model(Map.of(AgentModelConfig.PLAN, "*")), svc);
        assertThat(plan.isToolActive(executor(svc, "diskWriteFile"))).isFalse();
        assertThat(plan.isToolActive(shell)).isFalse();
        assertThat(plan.isToolActive(executor(svc, "diskReadFile"))).isTrue();
    }

    // UC-ATC-3
    @Test
    void absentOrBlankIsAll() {
        var svc = toolService();
        var shell = executor(svc, ShellTool.SHELL_RUN_COMMAND);

        // GIVEN no key at all
        var dev = new AiDevAgent(model(Map.of()), svc);
        assertThat(dev.isToolActive(shell)).isTrue();
        assertThat(dev.isMcpToolActive("mcp__docs__search_docs")).isTrue();

        // OR blank/separator-only values
        for (var blank : new String[] { " ", " , " }) {
            var blankDev = new AiDevAgent(model(Map.of(AgentModelConfig.DEV, blank)), svc);
            assertThat(blankDev.isToolActive(shell)).isTrue();
            assertThat(blankDev.isMcpToolActive("mcp__docs__search_docs")).isTrue();
        }
    }

    @Test
    void storeKeyRoundTripsAndCleanBreak() {
        // GIVEN a store with a raw CSV allowlist for the plan slot
        var store = new MapLlmConfigStore();
        var key = LlmConfigKeys.agentKey(AgentModelConfig.PLAN, LlmConfigKeys.AGENT_FIELD_TOOLS);
        store.put(key, "eclipse, !debugJava*");

        // WHEN the config is loaded
        var config = LlmConfigLoader.load(store);

        // THEN the raw value survives verbatim and other slots stay absent
        assertThat(config.toolAllowlist(AgentModelConfig.PLAN)).isEqualTo("eclipse, !debugJava*");
        assertThat(config.toolAllowlist(AgentModelConfig.DEV)).isNull();
        assertThat(config.toolAllowlist(AgentModelConfig.REVIEW)).isNull();

        // AND removing the key removes the filter (clean break, no migration)
        store.remove(key);
        assertThat(LlmConfigLoader.load(store).toolAllowlist(AgentModelConfig.PLAN)).isNull();
    }
}
