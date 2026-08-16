package org.sterl.llmpeon.tool.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.agent.NamedAgent;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.context.SimpleContextItem;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

class JonDelegateToolTest {

    private ConfiguredChatModel model;

    @BeforeEach
    void beforeEach() {
        var cm = new StreamMock().buildMock(req -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("SLAVE REPLY")).build());
        model = new ConfiguredChatModel(LlmConfig.newOllama("foo"), cm);
    }

    private AiAgent planSlave() { return new AiPlanAgent(model, new ToolService()); }
    private AiAgent devSlave()  { return new AiDevAgent(model, new ToolService()); }

    private JonDelegateTool newTool() {
        return newTool(List::of);
    }

    private JonDelegateTool newTool(java.util.function.Supplier<List<String>> memory) {
        return new JonDelegateTool(new NamedAgent("Da Thinka", planSlave()),
                new NamedAgent("Da Mek", devSlave()), memory);
    }

    @Test
    void talkPlan_drivesPlanSlave_andReturnsReply() {
        var tool = newTool();

        var reply = tool.talkPlan("make a plan");

        // WHEN
        assertThat(reply).contains("SLAVE REPLY");
        assertThat(tool.getPlanSlave().getMemory().containsUserMessage("make a plan")).isTrue();
        // AND
        // Token-Zahl ist plattformabhängig (lineSeparator im Prompt) — wir prüfen das Format des Kontext-Reports, nicht die Zahl.
        assertThat(reply).containsPattern("Context: \\d+ token - \\d+% used\\.");
    }

    @Test
    void askDev_drivesDevSlave_andReturnsReply() {
        // GIVEN
        var tool = newTool();

        // WHEN
        var reply = tool.askDev("what did you build?");
        
        // THEN
        assertThat(reply).contains("SLAVE REPLY");
        assertThat(tool.getDevSlave().getMemory().containsUserMessage("what did you build?")).isTrue();
        
        // AND
        // Token-Zahl ist plattformabhängig (lineSeparator im Prompt) — wir prüfen das Format des Kontext-Reports, nicht die Zahl.
        assertThat(reply).containsPattern("Context: \\d+ token - \\d+% used\\.");
    }

    /** planWithPlanAgent injects the plan-writing discipline as a standing order; talkPlan does not. */
    @Test
    void planWithPlanAgent_injectsPlanWriteLoop_talkPlanDoesNot() {
        var tool = newTool();

        tool.talkPlan("just a question");
        assertThat(tool.getPlanSlave().getRenderedTurnContext())
                .noneMatch(s -> s.contains("plan tools (planSave/planUpdate"));

        tool.planWithPlanAgent("write the plan");
        assertThat(tool.getPlanSlave().getRenderedTurnContext())
                .anyMatch(s -> s.contains("plan tools (planSave/planUpdate"));
    }

    /** ADR-0025: the slave is a shared singleton — the same instance across calls, context carries. */
    @Test
    void slaves_arePersistentSingletons() {
        var tool = newTool();

        var slave = tool.getPlanSlave();
        tool.talkPlan("first");
        tool.talkPlan("second");

        assertThat(tool.getPlanSlave()).isSameAs(slave); // same instance, reused
        assertThat(slave.getMemory().containsUserMessage("first")).isTrue();
        assertThat(slave.getMemory().containsUserMessage("second")).isTrue();
    }

    /** ADR-0024: the slaves are RAM-only — no history store, nothing persisted as JSON. */
    @Test
    void slaves_areRamOnly() {
        var tool = newTool();

        assertThat(tool.getPlanSlave().getMemory().isPersistent()).isFalse();
        assertThat(tool.getDevSlave().getMemory().isPersistent()).isFalse();
    }

    /** The shared memory is injected into each slave's standing orders (read-only for slaves). */
    @Test
    void slaves_getSharedMemoryInjected() {
        var tool = newTool(() -> List.of("MEMORY: always run the tests"));

        tool.talkPlan("go");
        tool.askDev("go");

        assertThat(tool.getPlanSlave().getRenderedTurnContext()).contains("MEMORY: always run the tests");
        assertThat(tool.getDevSlave().getRenderedTurnContext()).contains("MEMORY: always run the tests");
    }

    /**
     * Design-gap regression: the slaves must know the selected project, just like Jon does. The tool is
     * agnostic — whatever the injected provider carries (shared memory <em>and</em> the project line that
     * PeonAiService now folds in) reaches both slaves' standing orders.
     */
    @Test
    void slaves_getSelectedProjectInjected() {
        var tool = newTool(() -> List.of("MEMORY: run tests", "Selected project:\nDisk path: /ws/demo"));

        tool.talkPlan("go");
        tool.askDev("go");

        assertThat(tool.getPlanSlave().getRenderedTurnContext()).contains("Selected project:\nDisk path: /ws/demo");
        assertThat(tool.getDevSlave().getRenderedTurnContext()).contains("Selected project:\nDisk path: /ws/demo");
    }

    /**
     * Inc 1 (docs/sklaven-kontext-plan.md): the base AGENTS.md ground rules must reach the slaves too,
     * just like Jon. The tool is agnostic — PeonAiService folds the base AGENTS.md into the provider, so
     * whatever line it carries lands in both slaves' standing orders.
     */
    @Test
    void slaves_getBaseAgentsMdInjected() {
        var tool = newTool(() -> List.of("MEMORY: run tests", "AGENTS.md:\n---\n\nAlways build green."));

        tool.talkPlan("go");
        tool.askDev("go");

        assertThat(tool.getPlanSlave().getRenderedTurnContext()).contains("AGENTS.md:\n---\n\nAlways build green.");
        assertThat(tool.getDevSlave().getRenderedTurnContext()).contains("AGENTS.md:\n---\n\nAlways build green.");
    }

    /** S5: additionalContext is applied per slave agent name — each slave gets its own items. */
    @Test
    void additionalContext_appliedPerAgentName() {
        var tool = newTool();
        tool.setAdditionalContext(name -> List.of(
                new SimpleContextItem("AGENTS-" + name + ".md:\n---\n" + name + " rules")));

        tool.talkPlan("go");
        tool.askDev("go");

        assertThat(tool.getPlanSlave().getRenderedTurnContext())
                .anyMatch(s -> s.contains("Peon-Plan rules"));
        assertThat(tool.getDevSlave().getRenderedTurnContext())
                .anyMatch(s -> s.contains("Peon-Dev rules"));
    }

    /**
     * SAT2 (docs/sub-agent-timing.md): the done line carries the slave's elapsed wall-clock.
     * Name-agnostic: we assert the timing suffix, not the (flavourful) display name.
     */
    @Test
    void doneLine_carriesElapsedTime() {
        var tool = newTool();
        var lines = new ArrayList<String>();
        tool.monitor = (AiMonitor) (SimpleMessage m) -> lines.add(m.message());

        tool.talkPlan("go");

        assertThat(lines).anyMatch(l -> l.contains("done. (") && l.matches(".*\\(\\d+s\\)"));
    }

    /**
     * The build discipline (dev-build-loop.txt) rides only with buildWithDev — never on a plain askDev
     * question and never on the Plan slave.
     */
    @Test
    void devBuildLoop_injectedOnlyByBuildWithDev() {
        var tool = newTool();

        // plain dev question: no build-loop directive
        tool.askDev("just answer a question");
        assertThat(tool.getDevSlave().getRenderedTurnContext())
                .noneMatch(s -> s.contains("Task by task, never a red build"));

        // build call with a plan: the build loop rides along with the path
        tool.buildWithDev("implement it", "peon-plan/overview.md");
        assertThat(tool.getDevSlave().getRenderedTurnContext())
                .anyMatch(s -> s.contains("Task by task, never a red build"))
                .anyMatch(s -> s.contains("peon-plan/overview.md"));

        // the Plan slave never gets the dev build loop
        tool.talkPlan("make a plan");
        assertThat(tool.getPlanSlave().getRenderedTurnContext())
                .noneMatch(s -> s.contains("Task by task, never a red build"));
    }

    /** buildWithDev planPath goes into the Dev slave's standing orders and stays sticky across calls. */
    @Test
    void buildWithDev_planPath_becomesStickyStandingOrder() {
        var tool = newTool();

        tool.buildWithDev("start", "peon-plan/overview.md");
        assertThat(tool.getDevSlave().getRenderedTurnContext())
                .anyMatch(s -> s.contains("peon-plan/overview.md"));

        // a later plan-less build call must keep the plan path (survives compaction)
        tool.buildWithDev("continue", null);
        assertThat(tool.getDevSlave().getRenderedTurnContext())
                .anyMatch(s -> s.contains("peon-plan/overview.md"));
    }
}
