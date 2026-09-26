package org.sterl.llmpeon.tool.tools;

import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.poagent.AiPoAgent;

/**
 * R-TC-6/7: the shell-approval decision is a pure function of the call-time inputs
 * (mode + "is the turn owner autonomous?") — no state, no SWT, so the plugin can
 * evaluate it per call via a {@code Supplier<AiAgent>} and never goes stale.
 */
public final class ShellConfirmationPolicy {

    public enum Decision { PROMPT, AUTO_APPROVE }

    private ShellConfirmationPolicy() {
    }

    /** R-TC-7: the decision is a pure function of the CALL-TIME inputs. */
    public static Decision decide(ShellConfirmationMode mode, boolean autonomous) {
        return switch (mode) {
            case ALWAYS -> Decision.PROMPT;
            case NOT_AUTONOMOUS -> autonomous ? Decision.AUTO_APPROVE : Decision.PROMPT;
            case UNSET -> Decision.AUTO_APPROVE;
        };
    }

    /**
     * R-TC-6: autonomous = the active agent is Jon ({@link AiPoAgent}). Slaves inherit —
     * the turn owner decides, not the slave. Standalone Peon-Plan is NOT autonomous.
     * null → false (fail-closed: a missing agent never skips the prompt).
     */
    public static boolean isAutonomous(AiAgent agent) {
        return agent instanceof AiPoAgent;
    }
}
