package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.poagent.AiPoAgent;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.WriteValidator;

class AiPoAgentTest {

    private AiPoAgent newAgent() {
        var config = LlmConfig.newConfig(AiProvider.OLLAMA, "test-model", "http://localhost:9999");
        return new AiPoAgent(config.build(), new ToolService());
    }

    @Test
    void name_isPeonPO() {
        assertEquals("Peon-PO", newAgent().getName());
    }

    @Test
    void writeValidator_isDocs() {
        assertSame(WriteValidator.DOCS, newAgent().getWriteValidator());
    }

    @Test
    void systemPrompt_carriesTheMethodology() {
        var p = newAgent().getSystemPrompt();
        assertThat(p).contains("IST").contains("SOLL").contains("WEIL");
        assertThat(p).contains("GIVEN").contains("WHEN").contains("THEN");
        assertThat(p).contains("docs/");
    }

    /** The appended (German) delegation playbook steers the plan/build loop: Plan → Abnahme → Build → Review. */
    @Test
    void systemPrompt_carriesTheDelegationPlaybook() {
        var p = newAgent().getSystemPrompt();
        // Structural checks — proves po-delegation.txt was loaded, without duplicating tool names
        assertThat(p).contains("Bauen delegieren"); // section header from po-delegation.txt
        assertThat(p).contains("Rollen-Grenze"); // section header
        assertThat(p).contains("Da Thinka").contains("Da Mek"); // team members
        assertThat(p).contains("Plan vor Build"); // core rule
        assertThat(p).doesNotContain("${"); // no unresolved placeholders
    }

    /** Docs/plan paths are filled from PeonPaths — no unresolved ${...} placeholder leaks into the prompt. */
    @Test
    void systemPrompt_resolvesPathPlaceholders() {
        var p = newAgent().getSystemPrompt();
        assertThat(p).doesNotContain("${");
        assertThat(p).contains("docs/index.md").contains("peon-plan/overview.md");
    }

    /** Without wired slaves (e.g. headless) Jon's team is just himself as Da Boss. */
    @Test
    void getTeam_withoutSlaves_isJustDaBoss() {
        var po = newAgent();
        var team = po.getTeam();
        assertThat(team).extracting(NamedAgent::uiName).containsExactly("Da Boss");
        assertSame(po, team.get(0).agent());
    }

    /** ADR-0025: Da Boss (Jon) first, then the two ork slaves — on the shared instances, 0k idle. */
    @Test
    void getTeam_daBossFirst_thenTwoOrks_onSharedInstances_idle() {
        var config = LlmConfig.newConfig(AiProvider.OLLAMA, "test-model", "http://localhost:9999").build();
        var plan = new AiPlanAgent(config, new ToolService());
        var dev = new AiDevAgent(config, new ToolService());
        var po = new AiPoAgent(config, new ToolService(), null,
                List.of(new NamedAgent("Da Thinka", plan), new NamedAgent("Da Mek", dev)));

        var team = po.getTeam();

        assertThat(team).extracting(NamedAgent::uiName).containsExactly("Da Boss", "Da Thinka", "Da Mek");
        assertSame(po, team.get(0).agent());
        assertSame(plan, team.get(1).agent());
        assertSame(dev, team.get(2).agent());
        assertThat(team).allSatisfy(n -> assertThat(n.agent().getMemory().getTotalTokenUsed()).isZero());
    }

    @Test
    void otherAgents_defaultToAllowAll() {
        var config = LlmConfig.newConfig(AiProvider.OLLAMA, "m", "http://localhost:9999");
        assertSame(WriteValidator.ALLOW_ALL,
                new AiDevAgent(config.build(), new ToolService()).getWriteValidator());
    }

    @Test
    void agentModelName_defaultsToBaseModel_whenPoSlotEmpty() {
        var agent = newAgent();

        assertThat(agent.getAgentModelName()).isEqualTo("test-model");
    }

    @Test
    void setAgentModelName_writesPoSlot_notPlanSlot() {
        var model = LlmConfig.newConfig(AiProvider.OLLAMA, "base-model", "http://localhost:9999").build();
        var agent = new AiPoAgent(model, new ToolService());

        assertThat(agent.setAgentModelName("claude-x")).isTrue();
        assertThat(model.getConfig().modelConfigFor(AgentModelConfig.PO).model()).isEqualTo("claude-x");
        assertThat(model.getConfig().modelConfigFor(AgentModelConfig.PLAN).model()).isNull();
    }

    @Test
    void isThinkSupported_readsPoSlot_notPlan() {
        var config = LlmConfig.builder().providerType(AiProvider.OLLAMA).model("base-model")
                .url("http://localhost:9999")
                .modelConfigs(Map.of(AgentModelConfig.PLAN,
                        new AgentModelConfig(null, null, null, "high", null, null)))
                .build();
        var agent = new AiPoAgent(new ConfiguredChatModel(config), new ToolService());

        assertThat(agent.isThinkSupported()).isFalse();
    }
}
