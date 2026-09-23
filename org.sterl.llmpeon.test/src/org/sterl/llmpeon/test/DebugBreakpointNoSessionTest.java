package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.util.HashMap;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.jdt.debug.core.JDIDebugModel;
import org.junit.Test;
import org.sterl.llmpeon.parts.tools.debug.JavaDebugTool;

/**
 * debugJavaSetBreakpoint / debugJavaSetExceptionBreakpoint / debugJavaRemoveBreakpoint without a
 * debug session (docs/java-debugger-tool.md R-JD-13, UC-JD-15) — the persistent JDT markers are
 * created or removed and the response carries the honest no-session note instead of a VM-install
 * status. Real markers are created and must appear; cleanup in finally so no cross-run leeks.
 * The install path (marker → running VM) is verified manually (UC-JD-15).
 */
public class DebugBreakpointNoSessionTest extends AbstractIntegrationTest {

    private static final String ALPHA = "src/org/sterl/fixture/Alpha.java";

    /** Mirrors the JDT breakpoint marker types (jdt.debug 3.26.100, 2026-07 source). */
    private static final String LINE_BREAKPOINT_MARKER = "org.eclipse.jdt.debug.javaLineBreakpointMarker";
    private static final String EXCEPTION_BREAKPOINT_MARKER = "org.eclipse.jdt.debug.javaExceptionBreakpointMarker";

    /** JDT breakpoint marker attribute keys. */
    private static final String CONDITION_ATTR = "org.eclipse.jdt.debug.core.condition";
    private static final String HIT_COUNT_ATTR = "org.eclipse.jdt.debug.core.hitCount";
    private static final String TYPE_NAME_ATTR = "org.eclipse.jdt.debug.core.typeName";

    private static final String NO_SESSION_NOTE = "no active session — breakpoint stored as marker, installed when a session starts";

    private final JavaDebugTool tool = new JavaDebugTool();

    private static int launchCount() {
        return DebugPlugin.getDefault().getLaunchManager().getLaunches().length;
    }

    // UC-JD-15
    @Test
    public void setBreakpointWithoutSessionStoresMarker() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        assertEquals("premise: no launches in the test workbench", 0, launchCount());
        project.deleteMarkers(LINE_BREAKPOINT_MARKER, true, IResource.DEPTH_INFINITE);
        IMarker marker = null;
        try {
            // GIVEN: no session, the fixture project selected in the chat view
            tool.setCurrentProject(project);

            // WHEN: a line breakpoint is set by relative path without a session
            String result = tool.setBreakpoint(ALPHA, 5, "i > 3", 3, "THREAD");

            // THEN: the honest no-session note and no VM-install status
            assertTrue("expected the no-session note:\n" + result, result.contains(NO_SESSION_NOTE));
            assertFalse("no VM-install status without a session:\n" + result, result.contains("\"installed\""));

            // AND: a real line breakpoint marker exists in the selected project, condition + hit count intact
            marker = findMarkerById(markerIdFromResponse(result));
            assertNotNull("expected a line breakpoint marker:\n" + result, marker);
            assertEquals(LINE_BREAKPOINT_MARKER, marker.getType());
            assertEquals("i > 3", marker.getAttribute(CONDITION_ATTR, ""));
            assertEquals(3, marker.getAttribute(HIT_COUNT_ATTR, -1));
        } finally {
            deleteMarker(marker);
        }
    }

    // UC-JD-15
    @Test
    public void setBreakpointRelativePathWithoutSessionUsesCurrentProject() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        assertEquals("premise: no launches in the test workbench", 0, launchCount());
        project.deleteMarkers(LINE_BREAKPOINT_MARKER, true, IResource.DEPTH_INFINITE);
        IMarker marker = null;
        try {
            // GIVEN: no session, the fixture project selected in the chat view (the only project to resolve to)
            tool.setCurrentProject(project);

            // WHEN: a line breakpoint is set by a bare relative path (no /project/ prefix)
            String result = tool.setBreakpoint(ALPHA, 6, null, 0, "THREAD");

            // THEN: the marker lands in the selected project — resolved via currentProject, not a session
            marker = findMarkerById(markerIdFromResponse(result));
            assertNotNull("expected a marker in the selected project:\n" + result, marker);
            assertEquals("the marker must live in the chat-view-selected project",
                    project.getName(), marker.getResource().getProject().getName());
        } finally {
            deleteMarker(marker);
        }
    }

    // UC-JD-15
    @Test
    public void setBreakpointRelativePathNoSessionNoProjectFailsHonestly() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        assertEquals("premise: no launches in the test workbench", 0, launchCount());
        project.deleteMarkers(LINE_BREAKPOINT_MARKER, true, IResource.DEPTH_INFINITE);

        // GIVEN: no session and no project selected in the chat view (fresh tool, currentProject == null)

        // WHEN: a line breakpoint is set by relative path
        try {
            tool.setBreakpoint(ALPHA, 5, null, 0, "THREAD");
            fail("expected an honest no-project error");
        } catch (IllegalArgumentException e) {
            // THEN: the error names both the missing session-project attribute and the missing chat-view project
            assertTrue("expected the no-session part:\n" + e.getMessage(), e.getMessage().contains("no active debug session"));
            assertTrue("expected the no-chat-view-project part:\n" + e.getMessage(), e.getMessage().contains("no project is selected in the chat view"));
        }

        // AND: no marker was created
        assertEquals(0, project.findMarkers(LINE_BREAKPOINT_MARKER, true, IResource.DEPTH_INFINITE).length);
    }

    // UC-JD-15
    @Test
    public void removeBreakpointWithoutSessionRemovesMarker() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        assertEquals("premise: no launches in the test workbench", 0, launchCount());
        project.deleteMarkers(LINE_BREAKPOINT_MARKER, true, IResource.DEPTH_INFINITE);
        IMarker marker = null;
        try {
            // GIVEN: an existing line breakpoint marker, no session
            marker = JDIDebugModel.createLineBreakpoint(project.getFile(ALPHA), "org.sterl.fixture.Alpha",
                    5, -1, -1, 0, true, new HashMap<>()).getMarker();

            // WHEN: it is removed without a session
            String result = tool.removeBreakpoint(String.valueOf(marker.getId()));

            // THEN: removed + the same no-session note
            assertTrue("expected removed:\n" + result, result.contains("\"removed\" : true"));
            assertTrue("expected the no-session note:\n" + result, result.contains(NO_SESSION_NOTE));

            // AND: the marker is gone
            assertFalse("the marker must be removed", marker.exists());
        } finally {
            deleteMarker(marker);
        }
    }

    // UC-JD-15
    @Test
    public void setExceptionBreakpointWithoutSessionStoresMarker() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        assertEquals("premise: no launches in the test workbench", 0, launchCount());
        ResourcesPlugin.getWorkspace().getRoot().deleteMarkers(EXCEPTION_BREAKPOINT_MARKER, false, IResource.DEPTH_ZERO);
        IMarker marker = null;
        try {
            // GIVEN: no session
            String exceptionType = "java.lang.IllegalStateException";

            // WHEN: an exception breakpoint is set without a session
            String result = tool.setExceptionBreakpoint(exceptionType, "THREAD", null, null, null);

            // THEN: the no-session note and no VM-install status
            assertTrue("expected the no-session note:\n" + result, result.contains(NO_SESSION_NOTE));
            assertFalse("no VM-install status without a session:\n" + result, result.contains("\"installed\""));

            // AND: an exception breakpoint marker exists on the workspace root with the exception type
            marker = findMarkerById(markerIdFromResponse(result));
            assertNotNull("expected an exception breakpoint marker:\n" + result, marker);
            assertEquals(EXCEPTION_BREAKPOINT_MARKER, marker.getType());
            assertEquals(exceptionType, marker.getAttribute(TYPE_NAME_ATTR, ""));
        } finally {
            deleteMarker(marker);
        }
    }

    private static long markerIdFromResponse(String json) {
        var match = Pattern.compile("\"id\" : \"(\\d+)\"").matcher(json);
        if (!match.find()) {
            throw new AssertionError("no marker id in response:\n" + json);
        }
        return Long.parseLong(match.group(1));
    }

    private static IMarker findMarkerById(long id) throws CoreException {
        for (IMarker marker : ResourcesPlugin.getWorkspace().getRoot().findMarkers(IMarker.MARKER, true, IResource.DEPTH_INFINITE)) {
            if (marker.getId() == id) {
                return marker;
            }
        }
        return null;
    }

    private static void deleteMarker(IMarker marker) {
        if (marker != null && marker.exists()) {
            try {
                marker.delete();
            } catch (CoreException e) {
                // cleanup must not mask the test outcome
            }
        }
    }
}
