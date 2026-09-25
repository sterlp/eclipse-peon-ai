package org.sterl.llmpeon.parts.shell;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.parts.PeonConstants;
import org.sterl.llmpeon.parts.tools.AskUserTool;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.tools.ShellConfirmationMode;
import org.sterl.llmpeon.tool.tools.ShellConfirmationPolicy;
import org.sterl.llmpeon.tool.tools.ShellTool;

/**
 * R-TC-9: owns the shell-approval wiring. Stateless, reads the preference, configures the
 * shared ShellTool's provider — no SWT. The autonomy decision is evaluated per call
 * (R-TC-7) from the active-agent supplier, so the provider never goes stale on an
 * agent switch.
 */
public final class ShellApprovalService {

    private final ToolService sharedToolService;
    private final AskUserTool.QuestionPresenter questionPresenter;
    private final Supplier<AiAgent> activeAgent;

    public ShellApprovalService(ToolService sharedToolService,
                                AskUserTool.QuestionPresenter questionPresenter,
                                Supplier<AiAgent> activeAgent) {
        this.sharedToolService = sharedToolService;
        this.questionPresenter = questionPresenter;
        this.activeAgent = activeAgent;
    }

    /**
     * Called from AIChatView.applyConfig() — BEFORE the LlmConfig gate, on EVERY preference
     * change (idempotent, same pattern as applyMcpConfig). UNSET → no provider at all.
     */
    public void applyConfiguration() {
        String raw = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID)
                     .get(PeonConstants.PREF_SHELL_CONFIRMATION_ENABLED, "");
        ShellConfirmationMode mode = ShellConfirmationMode.of(raw);
        sharedToolService.getTool(ShellTool.class).ifPresent(shellTool -> {
            if (mode == ShellConfirmationMode.UNSET) {
                shellTool.setConfirmationProvider(null);
                return;
            }
            shellTool.setConfirmationProvider((command, dir) -> {
                // R-TC-7: autonomy is decided at CALL time from the active agent
                if (ShellConfirmationPolicy.decide(mode,
                        ShellConfirmationPolicy.isAutonomous(activeAgent.get()))
                        == ShellConfirmationPolicy.Decision.AUTO_APPROVE) {
                    return "Yes"; // ShellTool: "Yes" → the original command runs
                }
                // UI behaviour exactly as before (latch blocks the agent thread, the
                // presenter marshals to the UI thread)
                var latch = new CountDownLatch(1);
                var answer = new AtomicReference<>("No");
                questionPresenter.show(
                    "Approve execution of:\n\n`" + command + "`\n in **" + dir + "**? \n\n"
                    + "or enter a new command to execute:",
                    List.of("Yes", "No"),
                    (Consumer<String>) a -> { answer.set(a); latch.countDown(); });
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (AskUserTool.CANCEL.equals(answer.get())) {
                    throw new CancellationException("Canceled tool execution " + dir + " " + command);
                }
                return answer.get(); // "No" → ShellTool answers "Shell command execution denied!"
            });
        });
    }
}
