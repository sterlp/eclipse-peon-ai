package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.junit.Test;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.NamedAgent;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.model.CompactResult;
import org.sterl.llmpeon.parts.widget.AiAgentStatusWidget;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * R-CT-4 / UC-CT-6 (org.sterl.llmpeon.core/docs/compact-lock.md): a compacting member renders its own
 * 🟡 in the header roster instead of the generic 🟢. The render model is headless-tested in core
 * ({@code AiAgentStatusModelTest}); this is the SWT side — a real {@link AiAgentStatusWidget} in a
 * real shell, so a missing 🟡 branch in {@code text(i, Entry)} turns it red.
 * <p>
 * Honesty: {@link #idleAndWorkingRowsKeepTheirDots()} is the contrast pin — dropping the 🟢 branch or
 * rendering 🟡 unconditionally both fail one of the two tests.
 */
public class CompactRosterStatusTest extends AbstractSwtUiTest {

    // UC-CT-6
    @Test
    public void compactingRendersYellowDot() {
        ui(() -> {
            var roster = new AiAgentStatusWidget(shell, SWT.NONE,
                    () -> List.of(new NamedAgent("Probe Boss", stub(true, false)),
                            new NamedAgent("Probe Slave", stub(true, true))),
                    agent -> { }, () -> false);
            try {
                roster.layout(true, true);
                var labels = texts(roster.getChildren());
                assertEquals(2, labels.size());
                var boss = labels.get(0);
                var slave = labels.get(1);
                assertTrue("compacting slave must show the 🟡 Compact-Status: " + slave,
                        slave.contains("🟡 Probe Slave"));
                assertFalse("🟡 replaces 🟢, never both: " + slave,
                        slave.contains("🟢"));
                assertFalse("leaf rule: a compacting slave keeps Da Boss dark: " + boss,
                        boss.contains("🟢") || boss.contains("🟡"));
            } finally {
                roster.dispose();
            }
            return null;
        });
    }

    // UC-CT-6
    @Test
    public void idleAndWorkingRowsKeepTheirDots() {
        ui(() -> {
            var roster = new AiAgentStatusWidget(shell, SWT.NONE,
                    () -> List.of(new NamedAgent("Probe Boss", stub(false, false)),
                            new NamedAgent("Probe Slave", stub(true, false))),
                    agent -> { }, () -> false);
            try {
                roster.layout(true, true);
                var labels = texts(roster.getChildren());
                assertEquals(2, labels.size());
                assertTrue("a working slave still glows 🟢: " + labels.get(1),
                        labels.get(1).contains("🟢 Probe Slave"));
                assertFalse("an idle row shows no dot at all: " + labels.get(0),
                        labels.get(0).contains("🟢") || labels.get(0).contains("🟡"));
            } finally {
                roster.dispose();
            }
            return null;
        });
    }

    /** The roster label texts in row order — boss first, then the slaves. */
    private static List<String> texts(Control[] children) {
        return Arrays.stream(children)
                .filter(Label.class::isInstance).map(Label.class::cast)
                .map(Label::getText).toList();
    }

    private static AiAgent stub(boolean working, boolean compacting) {
        return new AiAgent() {
            @Override public String getName() { return "probe"; }
            @Override public String getSystemPrompt() { return ""; }
            @Override public ChatResponse call(String message, AiMonitor monitor) { return null; }
            @Override public CompactResult compact(AiMonitor monitor) { return CompactResult.skippedSmall(); }
            @Override public List<ChatMessage> buildStaticMessages(AiMonitor monitor) { return List.of(); }
            @Override public ThreadSafeMemory getMemory() { return new ThreadSafeMemory(); }
            @Override public int tokenContextUsedInPercent() { return 0; }
            @Override public void clear() { }
            @Override public boolean isToolActive(SmartToolExecutor exec) { return false; }
            @Override public boolean isMcpToolActive(String toolName) { return false; }
            @Override public boolean isWorking() { return working; }
            @Override public boolean isCompacting() { return compacting; }
        };
    }
}
