package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.junit.Test;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.compact.CompactResult;
import org.sterl.llmpeon.agent.NamedAgent;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.parts.widget.AiAgentStatusWidget;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;

import dev.langchain4j.data.message.ChatMessage;

/**
 * Regression guard for the FLAT roster (2026-09-14, Befund 1): each team member renders as a
 * Label plus (slaves only) a flat compact Button that are DIRECT children of the roster
 * composite — no per-member wrapper composite, which rendered as a gray box. A direct child
 * inherits the header background, so the button's paint fill equals the header color, like
 * the hammer.
 * <p>
 * Honesty: the structure assertions are the falsifiable regression guard (re-introducing a
 * wrapper composite turns them red). The background assertions are characterization of the CSS
 * contract: the roster CSS class resolves to white in the light theme (css/default-styles.css).
 * They are falsifiable too — drop the CSS class assignment and the controls fall back to the
 * default background (the shell's deliberate (200,220,240) proves the white is CSS-driven,
 * not inherited).
 */
public class HeaderRosterStructureTest extends AbstractSwtUiTest {

    @Test
    public void rosterIsFlatAndInheritsBackground() {
        ui(() -> {
            // deliberately NOT white: if the controls merely inherited the shell background,
            // the white assertion below fails
            var headerBg = new Color(display, 200, 220, 240);
            try {
                shell.setBackground(headerBg);
                var roster = new AiAgentStatusWidget(shell, SWT.NONE,
                        HeaderRosterStructureTest::stubTeam, agent -> { }, () -> false);
                try {
                    roster.layout(true, true);
                    var children = roster.getChildren();
                    // 2 members (boss + slave) → boss label, slave label, slave button — nothing else
                    assertEquals(3, children.length);
                    var labels = 0;
                    var buttons = 0;
                    for (var c : children) {
                        assertFalse("per-member wrapper composite re-introduced: " + c.getClass().getSimpleName(),
                                c instanceof Composite);
                        if (c instanceof Label) ++labels;
                        if (c instanceof Button) ++buttons;
                    }
                    assertEquals(2, labels);
                    assertEquals(1, buttons);
                    var button = firstButton(children);
                    // the one button is the slave's compact button, direct child of the roster
                    assertEquals("Compact Probe Slave", button.getToolTipText());
                    // CSS contract: the roster class resolves to white in the light theme —
                    // the shell's deliberate (200,220,240) proves it is CSS-driven, not inherited
                    var white = new RGB(255, 255, 255);
                    assertEquals(white, label(children).getBackground().getRGB());
                    assertEquals(white, button.getBackground().getRGB());
                } finally {
                    roster.dispose();
                }
            } finally {
                headerBg.dispose();
            }
            return null;
        });
    }

    private static Control firstButton(Control[] children) {
        for (var c : children) if (c instanceof Button b) return b;
        throw new AssertionError("no button among roster children");
    }

    private static Label label(Control[] children) {
        for (var c : children) if (c instanceof Label l) return l;
        throw new AssertionError("no label among roster children");
    }

    private static List<NamedAgent> stubTeam() {
        return List.of(new NamedAgent("Probe Boss", stubAgent()), new NamedAgent("Probe Slave", stubAgent()));
    }

    private static AiAgent stubAgent() {
        return new AiAgent() {
            @Override public String getName() { return "probe"; }
            @Override public String getSystemPrompt() { return ""; }
            @Override public dev.langchain4j.model.chat.response.ChatResponse call(String message, AiMonitor monitor) { return null; }
            @Override public CompactResult compact(AiMonitor monitor) { return CompactResult.skippedSmall(); }
            @Override public List<ChatMessage> buildStaticMessages(AiMonitor monitor) { return List.of(); }
            @Override public ThreadSafeMemory getMemory() { return new ThreadSafeMemory(); }
            @Override public int tokenContextUsedInPercent() { return 0; }
            @Override public void clear() { }
            @Override public boolean isToolActive(SmartToolExecutor exec) { return false; }
            @Override public boolean isMcpToolActive(String toolName) { return false; }
        };
    }
}
