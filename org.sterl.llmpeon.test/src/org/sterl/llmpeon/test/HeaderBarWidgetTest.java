package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.lang.reflect.Proxy;

import org.eclipse.core.resources.IProject;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.junit.Test;
import org.sterl.llmpeon.agent.NamedAgent;
import org.sterl.llmpeon.parts.ai.CodegraphConnectionService.Kind;
import org.sterl.llmpeon.parts.ai.CodegraphConnectionService.State;
import org.sterl.llmpeon.parts.ai.PeonAiService.ToolStatus;
import org.sterl.llmpeon.parts.widget.AiAgentStatusWidget;
import org.sterl.llmpeon.parts.widget.HeaderBarWidget;
import org.sterl.llmpeon.parts.widget.TokenHeaderWidget;

/**
 * Workbench-display test for the codegraph indicator in the {@link HeaderBarWidget} (R-CG-8,
 * Q2=(a)): the label sits between roster and hammer, mirrors the service state — tooltip verbatim
 * from {@link State#tooltip()}, dimmed (disabled) when OFF, platform STOP icon on ERROR.
 * Display/Shell lifecycle and UI-thread access come from {@link AbstractSwtUiTest}.
 */
public class HeaderBarWidgetTest extends AbstractSwtUiTest {

    // R-CG-8
    @Test
    public void headerBarHasCodegraphLabelBetweenRosterAndHammer() {
        // GIVEN a header bar in the test shell
        var bar = ui(this::newBar);

        // THEN it renders exactly five children: tokens · divider · roster · codegraph · hammer
        var children = ui(() -> bar.getChildren());
        assertEquals(5, children.length);
        assertTrue("tokens readout expected first", children[0] instanceof TokenHeaderWidget);
        assertTrue("divider expected second", children[1] instanceof Label);
        assertTrue("roster expected third", children[2] instanceof AiAgentStatusWidget);
        assertTrue("codegraph indicator expected fourth", children[3] instanceof Label);
        assertTrue("hammer expected last", children[4] instanceof Button);
    }

    // R-CG-8
    @Test
    public void codegraphStateOff_dimsTheCodegraphIconWithOffTooltip() {
        // GIVEN a header bar, WHEN the state is OFF (no project)
        var bar = ui(this::newBar);
        var state = new State(Kind.OFF, null, "no active project");
        ui(() -> {
            bar.setCodegraphState(state);
            return null;
        });

        // THEN the label is dimmed, carries the service tooltip verbatim and keeps the
        // codegraph icon (32x32 viewBox — not the 16x16 STOP icon)
        var label = codegraphLabel(bar);
        assertFalse("OFF must dim the indicator", label.isEnabled());
        assertEquals("Codegraph OFF: no active project", label.getToolTipText());
        assertCodegraphImage(label);
    }

    // R-CG-8
    @Test
    public void codegraphStateConnected_enablesTheCodegraphIconWithTooltip() {
        // GIVEN a header bar, WHEN the state is CONNECTED for a project
        var bar = ui(this::newBar);
        var state = new State(Kind.CONNECTED, project("cgproj"), "connected");
        ui(() -> {
            bar.setCodegraphState(state);
            return null;
        });

        // THEN the label is enabled, carries the service tooltip verbatim and shows the
        // codegraph icon
        var label = codegraphLabel(bar);
        assertTrue("CONNECTED must enable the indicator", label.isEnabled());
        assertEquals("Codegraph: cgproj — codegraph_explore available", label.getToolTipText());
        assertCodegraphImage(label);
    }

    // R-CG-8
    @Test
    public void codegraphStateError_showsTheStopIconEnabledWithTooltip() {
        // GIVEN a header bar, WHEN the state is ERROR for a project
        var bar = ui(this::newBar);
        var state = new State(Kind.ERROR, project("cgproj"), "spawn failed");
        ui(() -> {
            bar.setCodegraphState(state);
            return null;
        });

        // THEN the label is enabled, carries the service tooltip verbatim and shows the
        // platform STOP icon (16x16 — not the 32x32 codegraph icon)
        var label = codegraphLabel(bar);
        assertTrue("ERROR must enable the indicator", label.isEnabled());
        assertEquals("Codegraph error (cgproj): spawn failed", label.getToolTipText());
        assertStopImage(label);
    }

    private HeaderBarWidget newBar() {
        return new HeaderBarWidget(shell, SWT.NONE,
                () -> "Peon-PO",
                () -> List.<ToolStatus>of(),
                () -> List.<NamedAgent>of(),
                agent -> {},
                () -> false);
    }

    private static Label codegraphLabel(HeaderBarWidget bar) {
        return (Label) bar.getChildren()[3];
    }

    /** codegraph.svg carries a 32x32 viewBox — the STOP elcl16 icon renders 16x16. */
    private static void assertCodegraphImage(Label label) {
        assertNotNull("indicator must carry an image", label.getImage());
        var bounds = label.getImage().getBounds();
        assertEquals(32, bounds.width);
        assertEquals(32, bounds.height);
    }

    private static void assertStopImage(Label label) {
        assertNotNull("indicator must carry an image", label.getImage());
        var bounds = label.getImage().getBounds();
        assertEquals(16, bounds.width);
        assertEquals(16, bounds.height);
    }

    /** Minimal IProject stub — only {@code getName} is exercised by {@link State#tooltip()}. */
    private static IProject project(String name) {
        return (IProject) Proxy.newProxyInstance(
                IProject.class.getClassLoader(),
                new Class<?>[] { IProject.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "toString" -> "IProject:" + name;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }
}
