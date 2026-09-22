package org.sterl.llmpeon.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.debug.core.IJavaBreakpoint;
import org.eclipse.jdt.debug.core.IJavaExceptionBreakpoint;
import org.eclipse.jdt.debug.core.IJavaLineBreakpoint;
import org.eclipse.jdt.debug.core.JDIDebugModel;
import org.junit.Test;
import org.sterl.llmpeon.parts.tools.debug.JavaDebugTool;

/**
 * list_breakpoints (docs/java-debugger-tool.md R-JD-11, UC-JD-13) — the breakpoint map of
 * the selected project from persistent markers, without a debug session. Real markers are
 * created and must appear (self-verifying for marker types and attribute keys); cleanup in
 * finally so no cross-run leeks.
 */
public class DebugListBreakpointsTest extends AbstractIntegrationTest {

    private static final String ALPHA = "src/org/sterl/fixture/Alpha.java";

    /** Mirrors the JDT line breakpoint marker type (jdt.debug 3.26.100, 2026-07 source). */
    private static final String LINE_BREAKPOINT_MARKER = "org.eclipse.jdt.debug.javaLineBreakpointMarker";

    // UC-JD-13
    @Test
    public void lineBreakpointWithConditionAndHitCountAppears() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        IJavaLineBreakpoint breakpoint = null;
        try {
            // GIVEN: a real line breakpoint with condition and hit count in the fixture project
            breakpoint = JDIDebugModel.createLineBreakpoint(project.getFile(ALPHA), "org.sterl.fixture.Alpha",
                    5, -1, -1, 3, true, new HashMap<>());
            breakpoint.setCondition("i > 3");

            // WHEN: the seam lists the breakpoints of the project
            String result = JavaDebugTool.listBreakpoints(project);

            // THEN: the marker appears with type, location, condition, hit count and enabled state
            assertTrue("expected type line:\n" + result, result.contains("\"type\" : \"line\""));
            assertTrue("expected the project-relative file:\n" + result, result.contains("\"file\" : \"" + ALPHA + "\""));
            assertTrue("expected the line number:\n" + result, result.contains("\"line\" : 5"));
            assertTrue("expected the condition:\n" + result, result.contains("\"condition\" : \"i > 3\""));
            assertTrue("expected the hit count:\n" + result, result.contains("\"hitCount\" : 3"));
            assertTrue("expected enabled:\n" + result, result.contains("\"enabled\" : true"));
        } finally {
            deleteBreakpoint(breakpoint);
        }
    }

    // UC-JD-13
    @Test
    public void exceptionBreakpointListedWorkspaceWide() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        IJavaExceptionBreakpoint breakpoint = null;
        try {
            // GIVEN: an exception breakpoint — its marker lives on the workspace root
            breakpoint = JDIDebugModel.createExceptionBreakpoint(ResourcesPlugin.getWorkspace().getRoot(),
                    "java.lang.IllegalStateException", false, true, false, true, new HashMap<>());

            // WHEN: the seam lists the breakpoints of the fixture project
            String result = JavaDebugTool.listBreakpoints(project);

            // THEN: the workspace-wide exception breakpoint appears with its type
            assertTrue("expected type exception:\n" + result, result.contains("\"type\" : \"exception\""));
            assertTrue("expected the exception type:\n" + result,
                    result.contains("\"exceptionType\" : \"java.lang.IllegalStateException\""));
        } finally {
            deleteBreakpoint(breakpoint);
        }
    }

    // UC-JD-13
    @Test
    public void uiStyleBreakpointRendersClampedHitCount() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        IJavaLineBreakpoint breakpoint = null;
        try {
            // GIVEN: a line breakpoint without hit count (UI style) — the marker carries the JDT default -1
            breakpoint = JDIDebugModel.createLineBreakpoint(project.getFile(ALPHA), "org.sterl.fixture.Alpha",
                    6, -1, -1, 0, true, new HashMap<>());

            // WHEN: the seam lists the breakpoints of the project
            String result = JavaDebugTool.listBreakpoints(project);

            // THEN: the raw marker value -1 renders as 0 (R-JD-12 clamp on the marker read path)
            assertTrue("expected clamped hitCount 0:\n" + result, result.contains("\"hitCount\" : 0"));
            assertFalse("the raw marker value must not leak:\n" + result, result.contains("-1"));
        } finally {
            deleteBreakpoint(breakpoint);
        }
    }

    // UC-JD-13
    @Test
    public void noMarkersListsEmptyWithScopeDisclosure() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        // GIVEN: a clean slate — no JDT breakpoint markers in the project or on the workspace root
        project.deleteMarkers(LINE_BREAKPOINT_MARKER, true, IResource.DEPTH_INFINITE);
        ResourcesPlugin.getWorkspace().getRoot()
                .deleteMarkers("org.eclipse.jdt.debug.javaExceptionBreakpointMarker", false, IResource.DEPTH_ZERO);

        // WHEN: the seam lists the breakpoints of the project
        String result = JavaDebugTool.listBreakpoints(project);

        // THEN: an empty list with the project name and the scope disclosure
        assertTrue("expected an empty breakpoint list:\n" + result, result.contains("\"breakpoints\" : [ ]"));
        assertTrue("expected the project name:\n" + result,
                result.contains("\"project\" : \"" + PeonTestFixture.PROJECT_NAME + "\""));
        assertTrue("expected the scope disclosure:\n" + result,
                result.contains("\"scope\" : \"line breakpoints of the project + workspace exception breakpoints (other projects not listed)\""));
    }

    // UC-JD-13
    @Test
    public void otherProjectMarkersDoNotAppear() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        IMarker marker = null;
        try {
            // GIVEN: a line breakpoint marker directly on the workspace root (no project file behind it)
            marker = ResourcesPlugin.getWorkspace().getRoot().createMarker(LINE_BREAKPOINT_MARKER, Map.of(
                    IMarker.LINE_NUMBER, 7,
                    "org.eclipse.jdt.debug.core.typeName", "org.example.Other"));

            // WHEN: the seam lists the breakpoints of the fixture project
            String result = JavaDebugTool.listBreakpoints(project);

            // THEN: the root marker is not part of the project's line breakpoints
            assertFalse("a line breakpoint marker on the workspace root must not appear:\n" + result,
                    result.contains("org.example.Other"));
        } finally {
            if (marker != null && marker.exists()) {
                marker.delete();
            }
        }
    }

    private static void deleteBreakpoint(IJavaBreakpoint breakpoint) {
        if (breakpoint == null) {
            return;
        }
        try {
            breakpoint.delete();
        } catch (CoreException e) {
            // cleanup must not mask the test outcome
        }
    }
}
