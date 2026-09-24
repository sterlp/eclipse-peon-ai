package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.parts.PeonConstants;
import org.sterl.llmpeon.parts.shell.ShellApprovalService;
import org.sterl.llmpeon.poagent.AiPoAgent;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.tools.ShellTool;

/**
 * ShellApprovalService — R-TC-6/7/8 (docs/tool-confirmation.md). Headless: a fake
 * QuestionPresenter captures the prompt calls, the ShellTool runs harmless `echo` commands.
 */
public class ShellApprovalServiceTest extends AbstractUnitTest {

    private static final String PREF = PeonConstants.PREF_SHELL_CONFIRMATION_ENABLED;
    private static final String NODE = PeonConstants.PLUGIN_ID;

    private final ToolService toolService = new ToolService(); // auto-registers ShellTool
    private final AtomicInteger presenterCalls = new AtomicInteger();
    private final AtomicReference<String> lastQuestion = new AtomicReference<>();
    private final FakePresenter presenter = new FakePresenter();
    private final AtomicReference<AiAgent> activeAgent = new AtomicReference<>();
    private final ShellApprovalService service =
            new ShellApprovalService(toolService, presenter, activeAgent::get);

    private final class FakePresenter implements org.sterl.llmpeon.parts.tools.AskUserTool.QuestionPresenter {
        @Override
        public void show(String question, List<String> answers, Consumer<String> onAnswer) {
            presenterCalls.incrementAndGet();
            lastQuestion.set(question);
            onAnswer.accept("No");
        }
    }

    @Before
    public void setPreference() {
        // fixture BEFORE the SUT applies it; restored in @After (no cross-run state)
        activeAgent.set(null);
        InstanceScope.INSTANCE.getNode(NODE).put(PREF, "not-autonomous");
        service.applyConfiguration();
    }

    @After
    public void resetPreference() {
        InstanceScope.INSTANCE.getNode(NODE).remove(PREF);
        presenterCalls.set(0);
        lastQuestion.set(null);
    }

    private ShellTool shell() {
        return toolService.getTool(ShellTool.class).get();
    }

    private static ConfiguredChatModel model() {
        return LlmConfig.newOllama("x").build();
    }

    private static ToolService tools() {
        return new ToolService(false);
    }

    // R-TC-6: not-autonomous + Jon active → slave's shell call runs without prompt
    @Test
    public void notAutonomous_jon_active_noPrompt() throws Exception {
        // GIVEN Jon (AiPoAgent) is the active turn owner, pref "not-autonomous"
        activeAgent.set(new AiPoAgent(model(), tools()));
        service.applyConfiguration();

        // WHEN a shell call happens (e.g. Da Mek via the shared ShellTool)
        String result = shell().shellRunCommand("echo od6-ok", null, null, null, null);

        // THEN no prompt, the command ran
        assertEquals(0, presenterCalls.get());
        assertTrue("command must have run:\n" + result, result.contains("od6-ok"));
    }

    // R-TC-7: agentFlip_toSlave_prompts — the decision follows the call-time agent
    @Test
    public void agentFlip_toSlave_prompts() throws Exception {
        // GIVEN pref "not-autonomous", SUT applied with Peon-Plan active
        activeAgent.set(new AiPlanAgent(model(), tools()));
        service.applyConfiguration();

        // ... then the active agent flips to a non-autonomous one (no re-apply)
        activeAgent.set(new AiDevAgent(model(), tools()));

        // WHEN a shell call happens — the fake presenter answers "No"
        String result = shell().shellRunCommand("echo od7-denied", null, null, null, null);

        // THEN the prompt is shown and the denial is visible to the caller
        assertEquals(1, presenterCalls.get());
        assertTrue("question must name the command:\n" + lastQuestion.get(),
                lastQuestion.get().contains("echo od7-denied"));
        assertEquals("Shell command execution denied!", result.trim());
    }

    // R-TC-6: always_alwaysPrompts — even for autonomous agents
    @Test
    public void always_alwaysPrompts() throws Exception {
        // GIVEN pref "always", Peon-Plan (autonomous) active
        InstanceScope.INSTANCE.getNode(NODE).put(PREF, "always");
        activeAgent.set(new AiPlanAgent(model(), tools()));
        service.applyConfiguration();

        // WHEN a shell call happens — the fake presenter answers "No"
        String result = shell().shellRunCommand("echo od6-always", null, null, null, null);

        // THEN prompt shown, denied
        assertEquals(1, presenterCalls.get());
        assertEquals("Shell command execution denied!", result.trim());
    }

    // R-TC-8: unset_noProvider — legacy "true" and empty both mean UNSET (no prompt)
    @Test
    public void unset_noProvider() throws Exception {
        for (String raw : new String[] { "", "true" }) {
            // GIVEN pref unset / legacy "true"
            InstanceScope.INSTANCE.getNode(NODE).put(PREF, raw);
            // Jon active would be the case where a stale "true" used to prompt
            activeAgent.set(new AiPoAgent(model(), tools()));
            service.applyConfiguration();

            // WHEN a shell call happens
            String result = shell().shellRunCommand("echo od8-" + raw.length(), null, null, null, null);

            // THEN no prompt, the command ran
            assertEquals(0, presenterCalls.get());
            assertTrue("command must have run:\n" + result, result.contains("od8-" + raw.length()));
        }
    }

    // fail-closed: a missing (null) active agent never skips the prompt
    @Test
    public void nullActiveAgent_prompts() throws Exception {
        // GIVEN pref "not-autonomous", no active agent set (null → fail-closed)
        service.applyConfiguration();

        // WHEN a shell call happens — the fake presenter answers "No"
        String result = shell().shellRunCommand("echo od6-null", null, null, null, null);

        // THEN prompt shown, denied
        assertEquals(1, presenterCalls.get());
        assertFalse("must be denied:\n" + result, result.contains("od6-null"));
    }
}
