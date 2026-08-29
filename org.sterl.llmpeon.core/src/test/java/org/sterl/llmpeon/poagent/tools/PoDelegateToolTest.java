package org.sterl.llmpeon.poagent.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.agent.NamedAgent;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.context.SimpleContextItem;
import org.sterl.llmpeon.tool.ToolService;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

class PoDelegateToolTest {

    private StreamMock streamMock = new StreamMock();
    private ConfiguredChatModel model;

    @BeforeEach
    void beforeEach() {
        streamMock.reset();
        var cm = streamMock.buildMock(req -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("SLAVE REPLY")).build());
        model = new ConfiguredChatModel(LlmConfig.newOllama("foo"), cm);
    }

    private AiAgent planSlave() { return new AiPlanAgent(model, new ToolService()); }
    private AiAgent devSlave()  { return new AiDevAgent(model, new ToolService()); }

    private PoDelegateTool newTool() {
        return newTool(t -> List.of());
    }

    private PoDelegateTool newTool(Function<NamedAgent, List<ContextItem>> ordersFor) {
        return new PoDelegateTool(new NamedAgent("Da Thinka", planSlave()),
                new NamedAgent("Da Mek", devSlave()), ordersFor);
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

        // WHEN
        tool.talkPlan("just a question");
        // THEN
        var data = streamMock.getLastUserMessagesAsString();
        assertThat(data).hasSize(1);
        assertThat(data).contains("just a question");
        assertThat(data).doesNotContain("plan tools (planSave/planUpdate");

        // WHEN
        tool.planWithPlanAgent("write the plan");
        assertThat(streamMock.getLastUserMessagesAsString())
                .doesNotContain("plan tools (planSave/planUpdate");
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
        // GIVEN
        var memory = "MEMORY: always run the tests";
        var tool = newTool(t -> List.of(new SimpleContextItem("MEMORY: always run the tests")));

        // WHEN
        tool.talkPlan("go");
        tool.talkPlan("go");
        // THEN
        assertThat(streamMock.count("go")).isEqualTo(2);
        assertThat(streamMock.count(memory)).isEqualTo(1);
        
        // WHEN
        tool.askDev("go");
        tool.askDev("go");

        assertThat(streamMock.count("go")).isEqualTo(2);
        assertThat(streamMock.count(memory)).isEqualTo(1);
    }

    /** buildWithDev planPath goes into the Dev slave's standing orders and stays sticky across calls. */
    @Test
    void buildWithDev_planPath_becomesStickyStandingOrder() {
        // GIVEN
        var plan = "peon-plan/overview.md";
        var tool = newTool();

        tool.buildWithDev("start du ratte!", plan);
        
        assertThat(streamMock.count(plan)).isEqualTo(1);
        assertThat(streamMock.count("start du ratte!")).isEqualTo(1);

        // a later plan-less build call must keep the plan path (survives compaction)
        tool.buildWithDev("continue 2", null);
        tool.buildWithDev("continue 3", plan);
        assertThat(streamMock.count(plan)).isEqualTo(1);
        assertThat(streamMock.count("continue 3")).isEqualTo(1);
    }
}
