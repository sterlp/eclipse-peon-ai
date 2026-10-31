package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import java.util.List;

import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.ui.css.swt.theme.IThemeEngine;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.PlatformUI;
import org.junit.Test;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.model.CompactResult;
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
 * Honesty: {@link #rosterIsFlat()} is the falsifiable regression guard (re-introducing a
 * wrapper composite turns it red) — structure only, no theme dependency, always runs.
 * {@link #rosterInheritsCssBackground()} is characterization of the CSS contract: the roster
 * CSS class resolves to white in the light theme (css/default-styles.css); the shell's
 * deliberate (200,220,240) proves the white is CSS-driven, not inherited. It is falsifiable
 * too — drop the CSS class assignment and the controls fall back to the default background.
 * <p>
 * Theme pinning (verified 2026-10-17): {@link IThemeEngine#DEFAULT_THEME_ID}
 * ("org.eclipse.e4.ui.workbench.swt.theme.default") is NOT a registered theme id on the
 * 2026-09 platform — a setTheme with it silently stays on the dark theme (proven: active
 * theme remained e4_dark, the widget rendered #1E1E1E instead of white). The registered id
 * {@code org.eclipse.e4.ui.css.theme.e4_default} — the refid the plugin's plugin.xml uses for
 * css/default-styles.css — switches synchronously on the UI thread. When the environment
 * cannot make the light theme active at all (e.g. no registered themes in a recovered
 * workspace), the CSS test skips itself (Assume) — the structure guard still runs.
 */
@SuppressWarnings("restriction")
public class HeaderRosterStructureTest extends AbstractSwtUiTest {

    /** Registered light theme id — the refid the plugin's plugin.xml uses for css/default-styles.css. */
    private static final String LIGHT_THEME_ID = "org.eclipse.e4.ui.css.theme.e4_default";

    @Test
    public void rosterIsFlat() {
        ui(() -> {
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
            } finally {
                roster.dispose();
            }
            return null;
        });
    }

    @Test
    public void rosterInheritsCssBackground() {
        // The Assume runs BEFORE the ui-call so an AssumptionViolatedException is not wrapped
        // into an AssertionError by ui().
        var engine = themeEngine();
        assumeTrue("no E4 theme engine available", engine != null);
        // Pin the registered light theme — synchronous on the UI thread (class javadoc: why
        // IThemeEngine.DEFAULT_THEME_ID must not be used).
        ui(() -> {
            engine.setTheme(LIGHT_THEME_ID, false);
            return null;
        });
        // CSS precondition: the light theme must actually be active for the white assertions.
        // When the environment cannot provide it (e.g. no registered themes), skip the CSS
        // characterization — the structure guard (rosterIsFlat) still runs.
        assumeTrue("light theme not active after pin — CSS precondition unmet",
                engine.getActiveTheme() != null && LIGHT_THEME_ID.equals(engine.getActiveTheme().getId()));
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
                    // CSS contract: the roster class resolves to white in the light theme —
                    // the shell's deliberate (200,220,240) proves it is CSS-driven, not inherited
                    var white = new RGB(255, 255, 255);
                    assertEquals(white, label(children).getBackground().getRGB());
                    assertEquals(white, firstButton(children).getBackground().getRGB());
                } finally {
                    roster.dispose();
                }
            } finally {
                headerBg.dispose();
            }
            return null;
        });
    }

    /** The workbench's E4 CSS theme engine (PlatformUI → IEclipseContext → IThemeEngine), or null. */
    private static IThemeEngine themeEngine() {
        if (!PlatformUI.isWorkbenchRunning()) return null;
        var context = PlatformUI.getWorkbench().getService(IEclipseContext.class);
        return context == null ? null : context.get(IThemeEngine.class);
    }

    private static Control firstButton(Control[] children) {
        for (var c : children) if (c instanceof Button b) return b;
        throw new AssertionError("no button among roster children");
    }

    private static Label label(Control[] children) {
        for (var c : children) if (c instanceof Label l) return l;
        throw new AssertionError("no label among roster children");
    }

    private static List<AiAgent> stubTeam() {
        return List.of(stubAgent("Probe Boss"), stubAgent("Probe Slave"));
    }

    private static AiAgent stubAgent(String name) {
        return new AiAgent() {
            @Override public String getName() { return name; }
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
