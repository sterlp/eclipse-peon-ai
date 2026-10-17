package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
import org.sterl.llmpeon.tool.tools.AbstractTool;
import org.sterl.llmpeon.tool.tools.DiskFileReadTool;
import org.sterl.llmpeon.tool.tools.DiskFileWriteTool;
import org.sterl.llmpeon.tool.tools.SearchAgentTool;
import org.sterl.llmpeon.tool.tools.ShellTool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

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

    /** Preview (R-ATC-8): built-in stubs with fixed names — name only, no behaviour (never executed). */
    static class StubTools extends AbstractTool {
        @Tool(name = "eclipseGrepFiles", value = "stub for the allowlist tests (name only)")
        public String eclipseGrepFiles(@P(name = "query") String query) {
            return "";
        }

        @Tool(name = "debugJavaSetBreakpoint", value = "stub for the allowlist tests (name only)")
        public String debugJavaSetBreakpoint(@P(name = "query") String query) {
            return "";
        }
    }

    /** A ToolService with a fake connected-MCP name list (unit tests have no real MCP connection). */
    static class ToolServiceWithMcp extends ToolService {
        private final List<String> mcpNames;

        ToolServiceWithMcp(List<String> mcpNames) {
            super(false);
            this.mcpNames = List.copyOf(mcpNames);
        }

        @Override
        public List<String> mcpToolNames() {
            return mcpNames;
        }
    }

    // UC-ATC-7
    @Test
    void resolveActiveToolsPlanEclipseMinusDebug() {
        // GIVEN a plan slot with the typed allowlist "eclipse, !debugJava*"
        var csv = "eclipse, !debugJava*";
        var svc = new ToolServiceWithMcp(List.of("mcp__docs__search_docs", "eclipseMcp"));
        svc.addTool(new ShellTool());
        svc.addTool(new DiskFileReadTool(Path.of(System.getProperty("java.io.tmpdir"))));
        svc.addTool(new StubTools());
        var plan = new AiPlanAgent(model(Map.of(AgentModelConfig.PLAN, csv)), svc);

        // WHEN the preview resolves the typed value
        var names = plan.resolveActiveTools(csv);

        // THEN the eclipse* tools stay (built-in AND MCP), everything else is out
        assertThat(names).containsExactly("eclipseGrepFiles", "eclipseMcp");
    }

    @Test
    void resolveActiveToolsMatchesRuntimeFilter() {
        // GIVEN a stored allowlist and a preview of the SAME value
        var csv = "eclipse, disk, !debugJava*";
        var svc = new ToolServiceWithMcp(List.of("mcp__docs__search_docs", "eclipseMcp", "mcp__other__x"));
        svc.addTool(new ShellTool());
        svc.addTool(new DiskFileReadTool(Path.of(System.getProperty("java.io.tmpdir"))));
        svc.addTool(new DiskFileWriteTool(Path.of(System.getProperty("java.io.tmpdir"))));
        svc.addTool(new StubTools());
        var plan = new AiPlanAgent(model(Map.of(AgentModelConfig.PLAN, csv)), svc);

        // WHEN the preview resolves the stored CSV
        var preview = plan.resolveActiveTools(csv);

        // THEN it matches exactly what the runtime filter would send (one mechanism, R-ATC-8)
        var runtime = new ArrayList<String>();
        svc.getExecutors().stream()
                .filter(plan::isToolActive)
                .map(e -> e.getSpec().name())
                .forEach(runtime::add);
        svc.mcpToolNames().stream()
                .filter(plan::isMcpToolActive)
                .forEach(runtime::add);
        runtime.sort(String::compareTo);
        assertThat(preview).containsExactlyElementsOf(runtime);
        // sanity: the set is non-trivial (allow prefixes, deny, class filter all exercised)
        assertThat(preview).containsExactly(
                "diskListDirectory", "diskReadFile", "diskSearchFiles", "eclipseGrepFiles", "eclipseMcp");
    }

    @Test
    void resolveActiveToolsNegationOnlyAllMinus() {
        // GIVEN a review slot with a negations-only allowlist (all tools minus shell*)
        var svc = new ToolServiceWithMcp(List.of("mcp__docs__search_docs", "shellMcp"));
        svc.addTool(new ShellTool());
        svc.addTool(new DiskFileReadTool(Path.of(System.getProperty("java.io.tmpdir"))));
        svc.addTool(new DiskFileWriteTool(Path.of(System.getProperty("java.io.tmpdir"))));
        svc.addTool(new StubTools());
        var review = new AiReviewAgent(model(Map.of(AgentModelConfig.REVIEW, "!shell*")), svc);

        // WHEN the preview resolves the typed value
        var names = review.resolveActiveTools("!shell*");

        // THEN everything except shell* is active (edit tools stay out via the class filter)
        assertThat(names).containsExactly(
                "debugJavaSetBreakpoint", "diskListDirectory", "diskReadFile", "diskSearchFiles",
                "eclipseGrepFiles", "mcp__docs__search_docs", "readOperationSystemInformation");
    }

    @Test
    void resolveActiveToolsEmptyCsvAllowsAll() {
        // GIVEN no allowlist (absent = all)
        var svc = new ToolServiceWithMcp(List.of("mcp__docs__search_docs"));
        svc.addTool(new ShellTool());
        svc.addTool(new DiskFileReadTool(Path.of(System.getProperty("java.io.tmpdir"))));
        var dev = new AiDevAgent(model(Map.of()), svc);

        // WHEN the preview resolves an empty or absent value
        assertThat(dev.resolveActiveTools("")).containsExactly(
                "diskListDirectory", "diskReadFile", "diskSearchFiles",
                "mcp__docs__search_docs", "readOperationSystemInformation", "shellRunCommand");
        assertThat(dev.resolveActiveTools(null)).containsExactly(
                "diskListDirectory", "diskReadFile", "diskSearchFiles",
                "mcp__docs__search_docs", "readOperationSystemInformation", "shellRunCommand");
    }

    @Test
    void searchResolveActiveToolsRespectsClassFilterAndAllowlist() {
        // GIVEN the search slot's allowlist on the sub-loop's own tool service
        var csv = "eclipse, !debugJava*";
        var svc = new ToolServiceWithMcp(List.of("eclipseMcp", "mcp__other__x"));
        svc.addTool(new ShellTool());
        svc.addTool(new DiskFileReadTool(Path.of(System.getProperty("java.io.tmpdir"))));
        svc.addTool(new StubTools());
        var search = new SearchAgentTool(svc);

        // WHEN the preview resolves the typed value
        var names = search.resolveActiveTools(csv);

        // THEN the sub-loop's class filter (no shell, no edit tools) AND the allowlist apply
        assertThat(names).containsExactly("eclipseGrepFiles", "eclipseMcp");
    }

    // R-CG-6
    @Test
    void codegraphExploreVisibleWithoutAllowlist() {
        // GIVEN a connected Codegraph server (default toolset: codegraph_explore only)
        var svc = new ToolServiceWithMcp(List.of("codegraph_explore"));
        svc.addTool(new ShellTool());
        svc.addTool(new DiskFileReadTool(Path.of(System.getProperty("java.io.tmpdir"))));
        var dev = new AiDevAgent(model(Map.of()), svc);

        // THEN no allowlist (absent = all): the MCP tool is visible at runtime AND in the preview
        assertThat(dev.isMcpToolActive("codegraph_explore")).isTrue();
        assertThat(dev.resolveActiveTools(null)).contains("codegraph_explore");
    }

    // R-CG-6
    @Test
    void codegraphExploreTokenPrefixMatchesAndForeignTokenDoesNot() {
        // GIVEN a connected Codegraph server and explicit allowlists
        var svc = new ToolServiceWithMcp(List.of("codegraph_explore"));
        svc.addTool(new ShellTool());
        var withToken = new AiDevAgent(model(Map.of(AgentModelConfig.DEV, "codegraph")), svc);
        var withoutToken = new AiDevAgent(model(Map.of(AgentModelConfig.DEV, "eclipse")), svc);

        // THEN the "codegraph" token matches the tool by prefix, a foreign token hides it
        assertThat(withToken.isMcpToolActive("codegraph_explore")).isTrue();
        assertThat(withoutToken.isMcpToolActive("codegraph_explore")).isFalse();
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
