package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.eclipse.debug.core.DebugPlugin;
import org.junit.Test;
import org.sterl.llmpeon.parts.tools.debug.JavaDebugTool;

/**
 * Java debug tool (docs/java-debugger-tool.md R-JD-1…5, UC-JD-1…6).
 * Automated coverage is session-free: the honest no-session message of every
 * session-bound action (UC-JD-1) plus the DebugJson walker (DebugJsonUnitTest).
 * The breakpoint marker ops work without a session (R-JD-13,
 * DebugBreakpointNoSessionTest). Live-session behavior is verified manually
 * (smoke) — the PDE test workbench cannot host a reliable in-workbench Java
 * debug launch (JDWP initial-suspend race, 2026-09-20).
 */
public class JavaDebugToolTest extends AbstractIntegrationTest {

    private final JavaDebugTool tool = new JavaDebugTool();

    private static int launchCount() {
        return DebugPlugin.getDefault().getLaunchManager().getLaunches().length;
    }

    // UC-JD-1
    // 11 session-bound actions; the 3 breakpoint marker ops work without a session (R-JD-13, DebugBreakpointNoSessionTest) and debugJavaListBreakpoints is the deliberate read exception (R-JD-11, DebugListBreakpointsTest).
    @Test
    public void noSessionFailsHonest() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        // GIVEN: no active debug session
        assertEquals("premise: no launches in the test workbench", 0, launchCount());

        // WHEN: every session-bound action is called without a session
        var results = new String[] {
                tool.getState(),
                tool.getStackTrace(null),
                tool.getVariables(null, 0, null, 0),
                tool.evaluateExpression(null, 0, "x + 1", 0),
                tool.getException(null),
                tool.setVariable(null, 0, "x", "1"),
                tool.stepOver(null, 15000),
                tool.stepIn(null, 15000),
                tool.stepOut(null, 15000),
                tool.resume(null, 30000),
                tool.suspend()
        };

        // THEN: every call answers with the honest no-session message + start hint
        for (String result : results) {
            assertTrue("expected the no-session message:\n" + result,
                    result.contains("no active debug session")
                            && result.contains("start debugging in the Debug view first"));
        }

        // AND: no auto-start — still no launch (R-JD-1)
        assertEquals("no auto-start expected", 0, launchCount());
    }

    // UC-JD-13
    @Test
    public void listBreakpointsDoesNotFailWithoutSession() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        // GIVEN: no active debug session and no project selected on the tool instance
        assertEquals("premise: no launches in the test workbench", 0, launchCount());

        // WHEN: debugJavaListBreakpoints is called
        String result = tool.listBreakpoints();

        // THEN: the honest no-project error — and NOT the no-session message (R-JD-11 exception)
        assertTrue("expected the no-project message:\n" + result, result.contains("no project selected"));
        assertFalse("must not answer with the no-session message:\n" + result,
                result.contains("no active debug session"));
    }
}
