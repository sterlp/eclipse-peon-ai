package org.sterl.llmpeon.tool.tools;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.poagent.AiPoAgent;
import org.sterl.llmpeon.scaffold.AiScaffoldAgent;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.tools.ShellConfirmationPolicy.Decision;
import org.sterl.llmpeon.tool.tools.ShellConfirmationMode;

class ShellConfirmationPolicyTest {

    // R-TC-8: mode_of_matrix
    @Test
    void modeOfMatrix() {
        // GIVEN/WHEN raw preference values — legacy "true" is NOT a live value (clean break)
        assertThat(ShellConfirmationMode.of("true")).isEqualTo(ShellConfirmationMode.UNSET);
        assertThat(ShellConfirmationMode.of("false")).isEqualTo(ShellConfirmationMode.UNSET);
        assertThat(ShellConfirmationMode.of("")).isEqualTo(ShellConfirmationMode.UNSET);
        assertThat(ShellConfirmationMode.of(null)).isEqualTo(ShellConfirmationMode.UNSET);
        assertThat(ShellConfirmationMode.of("bogus")).isEqualTo(ShellConfirmationMode.UNSET);

        // THEN the two live values, trim + case-insensitive
        assertThat(ShellConfirmationMode.of("ALWAYS")).isEqualTo(ShellConfirmationMode.ALWAYS);
        assertThat(ShellConfirmationMode.of(" Always ")).isEqualTo(ShellConfirmationMode.ALWAYS);
        assertThat(ShellConfirmationMode.of("not-autonomous")).isEqualTo(ShellConfirmationMode.NOT_AUTONOMOUS);
    }

    // R-TC-6: decide_always_prompts_even_when_autonomous
    @Test
    void decideAlwaysPromptsEvenWhenAutonomous() {
        // GIVEN mode always, autonomous turn owner
        // WHEN
        var decision = ShellConfirmationPolicy.decide(ShellConfirmationMode.ALWAYS, true);
        // THEN prompt regardless of autonomy
        assertThat(decision).isEqualTo(Decision.PROMPT);
    }

    // R-TC-6: decide_not_autonomous_autonomous_approves
    @Test
    void decideNotAutonomousAutonomousApproves() {
        // GIVEN mode not-autonomous, autonomous turn owner (Jon or Peon-Plan)
        // WHEN
        var decision = ShellConfirmationPolicy.decide(ShellConfirmationMode.NOT_AUTONOMOUS, true);
        // THEN no prompt
        assertThat(decision).isEqualTo(Decision.AUTO_APPROVE);
    }

    // R-TC-6: decide_not_autonomous_slave_prompts
    @Test
    void decideNotAutonomousSlavePrompts() {
        // GIVEN mode not-autonomous, non-autonomous turn owner (e.g. Peon-Dev)
        // WHEN
        var decision = ShellConfirmationPolicy.decide(ShellConfirmationMode.NOT_AUTONOMOUS, false);
        // THEN prompt
        assertThat(decision).isEqualTo(Decision.PROMPT);
    }

    // R-TC-7: decide_call_time_flip
    @Test
    void decideCallTimeFlip() {
        // GIVEN same mode, the autonomous flag flips between two calls (call-time evaluation)
        // WHEN
        var first = ShellConfirmationPolicy.decide(ShellConfirmationMode.NOT_AUTONOMOUS, true);
        var second = ShellConfirmationPolicy.decide(ShellConfirmationMode.NOT_AUTONOMOUS, false);
        // THEN the decision follows the call-time input, no frozen state
        assertThat(first).isEqualTo(Decision.AUTO_APPROVE);
        assertThat(second).isEqualTo(Decision.PROMPT);
    }

    // R-TC-6: isAutonomous_matrix
    @Test
    void isAutonomousMatrix() {
        var model = LlmConfig.newOllama("x").build();
        var toolService = new ToolService(false);

        // THEN Jon (AiPoAgent) and Peon-Plan (AiPlanAgent) are autonomous
        assertThat(ShellConfirmationPolicy.isAutonomous(new AiPoAgent(model, toolService))).isTrue();
        assertThat(ShellConfirmationPolicy.isAutonomous(new AiPlanAgent(model, toolService))).isTrue();

        // ... everything else is not — including null (fail-closed)
        assertThat(ShellConfirmationPolicy.isAutonomous(new AiDevAgent(model, toolService))).isFalse();
        assertThat(ShellConfirmationPolicy.isAutonomous(new AiScaffoldAgent(model, new SkillService()))).isFalse();
        assertThat(ShellConfirmationPolicy.isAutonomous(null)).isFalse();
    }
}
