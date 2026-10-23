package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.scaffold.AiScaffoldAgent;
import org.sterl.llmpeon.tool.tools.SearchAgentTool;

import dev.langchain4j.agent.tool.ToolSpecification;

/**
 * R-WEB-11 / UC-WEB-11 — registration <em>is</em> the distribution rule for {@code webSearch}:
 * every agent gets it (it is not an edit tool), the Search sub-agent included, the curated
 * Peon-PO set excluded (org.sterl.llmpeon.core/docs/web-tools.md). These tests lock the three
 * registration decisions, not the rendering (that is {@code WebSearchToolTest}).
 */
class WebSearchRegistrationTest {

    private static ToolService withDefaults() {
        return new ToolService();
    }

    private static List<String> names(ToolService service) {
        return service.toolSpecifications().stream().map(ToolSpecification::name).toList();
    }

    // UC-WEB-11
    @Test
    void webSearchIsRegisteredInDefaultToolService() {
        // GIVEN the default registry — core agents and the plugin's SharedToolsComponent both build on it
        var service = withDefaults();

        // WHEN / THEN
        assertThat(names(service)).contains("webSearch");
        // AND: same distribution as webFetchAsMarkdown, its reading-in-the-web sibling (R-WEB-11)
        assertThat(names(service)).contains("webFetchAsMarkdown");
    }

    // UC-WEB-11
    @Test
    void webSearchSurvivesSearchAgentEditToolFilter() {
        // GIVEN the real sub-agent filter, taken from the registered tool — not a copy of it
        var service = withDefaults();
        var filter = service.getTool(SearchAgentTool.class).orElseThrow().getFilter();

        // WHEN
        var names = service.activeNames(filter, name -> true);

        // THEN — a web read tool stays available to the Search sub-agent
        assertThat(names).contains("webSearch");
        // AND: the filter really ran — the non-edit tools of this registry are the ones it drops by design
        assertThat(names).doesNotContain("searchAgent", "shellRunCommand", "readOperationSystemInformation",
                "compactSession");
    }

    // UC-WEB-11
    @Test
    void webSearchIsRegisteredForScaffoldAgent() throws Exception {
        // GIVEN the scaffold agent: it builds its own registry via ToolService(false), so defaults do not help it
        var config = LlmConfig.builder()
                .providerType(AiProvider.OLLAMA)
                .model("qwen3")
                .url("http://localhost:9999")
                .configDir(Files.createTempDirectory("scaffold-websearch"))
                .build();

        // WHEN
        var names = names(new AiScaffoldAgent(config.build()).getToolService());

        // THEN
        assertThat(names).contains("webSearch");
        // AND: alongside the web tool it already had — the scaffold keeps its curated disk set
        assertThat(names).contains("webFetchAsMarkdown", "diskReadFile", "diskWriteFile", "diskGrepFiles");
    }

    // UC-WEB-11
    @Test
    void webSearchIsNotInPoRegistry() {
        // GIVEN the constructor the Peon-PO registry uses (BuildPoAgentComponent: ToolService(false) plus
        //        its curated docs-only tool list — no web tool among them)
        var po = new ToolService(false);

        // WHEN / THEN — R-WEB-11 wörtlich: the PO has no webFetchAsMarkdown, so he gets no webSearch either
        assertThat(names(po)).doesNotContain("webSearch", "webFetchAsMarkdown");
        // AND: positive control — the tool is registered, just not for the PO; without this the
        //      negative assert above would also be green for a missing registration
        assertThat(names(withDefaults())).contains("webSearch");
    }
}
