package org.sterl.llmpeon.parts.tools.debug;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.debug.core.IJavaBreakpoint;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaExceptionBreakpoint;
import org.eclipse.jdt.debug.core.IJavaLineBreakpoint;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.eclipse.jdt.debug.core.JDIDebugModel;
import org.eclipse.jdt.debug.eval.EvaluationManager;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;
import org.eclipse.jdt.debug.eval.IEvaluationResult;
import org.sterl.llmpeon.tool.tools.AbstractTool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * Reads and drives a user-started Java debug session (JDT debug model, R-JD-4):
 * state, stack, variables, expression evaluation, variables, breakpoints and steps.
 * Stateless — every call re-resolves the session (R-JD-3). Never auto-starts and
 * never auto-disconnects a session (R-JD-1): without an active session every
 * action answers with the honest no-session message — except debugJavaListBreakpoints
 * (R-JD-11): it works without a session, reading the persistent breakpoint
 * markers of the project selected in the chat view instead.
 *
 * Optional numeric parameters follow "0 = unset" (D3).
 */
public class JavaDebugTool extends AbstractTool {

    /** JDT breakpoint marker types (jdt.debug 3.26.100, 2026-07: JDTDebugConstants is gone, the ids live in the internal breakpoint classes). */
    private static final String LINE_BREAKPOINT_MARKER = "org.eclipse.jdt.debug.javaLineBreakpointMarker";
    private static final String EXCEPTION_BREAKPOINT_MARKER = "org.eclipse.jdt.debug.javaExceptionBreakpointMarker";

    private static final String NO_PROJECT = "no project selected — select a project to list its breakpoints";

    private IProject currentProject;

    public void setCurrentProject(IProject currentProject) {
        this.currentProject = currentProject;
    }

    @Override
    public boolean isEditTool() { return true; }

    @Tool(name = "debugJavaGetState", value = "Show the active Java debug session: VM state, all threads with state, system flag and top frame.")
    public String getState() {
        var session = DebugSession.findActive();
        if (session == null) {
            return noSession("debugJavaGetState");
        }
        return DebugJson.state(session);
    }
    @Tool(name = "debugJavaGetStackTrace", value = "List the stack frames of a debug thread (index, method, type, line, method entry). Optional thread name.")
    public String getStackTrace(@P(name = "thread", description = "Thread name; empty = first suspended thread, else first non-system thread.", required = false) String thread) {
        var session = DebugSession.findActive();
        if (session == null) {
            return noSession("debugJavaGetStackTrace");
        }
        return DebugJson.stackTrace(session.resolveThread(thread));
    }

    @Tool(name = "debugJavaGetVariables", value = "Show the variables of a stack frame as JSON: the frame locals plus the static fields of the frame's declaring type in a separate `statics` block (empty when none). Optional name path (a.b.c) and depth (default 1, max 5).")
    public String getVariables(@P(name = "thread", description = "Thread name; empty = first suspended thread, else first non-system thread.", required = false) String thread,
            @P(name = "frame", description = "Stack frame index, 0 = top.", required = false) Integer frame,
            @P(name = "name", description = "Variable path (a.b.c) to drill into; empty = all top-level variables.", required = false) String name,
            @P(name = "depth", description = "Nesting depth for object fields, 1..5; 0 = default 1.", required = false) Integer depth) {
        var session = DebugSession.findActive();
        if (session == null) {
            return noSession("debugJavaGetVariables");
        }
        return DebugJson.variables(javaFrame(session, thread, frame), name, depth == null ? 0 : depth);
    }

    @Tool(name = "debugJavaEvaluateExpression", value = "Evaluate a Java expression in a suspended stack frame and return the result as JSON. Object results render their fields to depth 2; primitives, String and null come back as values.")
    public String evaluateExpression(@P(name = "thread", description = "Thread name; empty = first suspended thread, else first non-system thread.", required = false) String thread,
            @P(name = "frame", description = "Stack frame index, 0 = top.", required = false) Integer frame,
            @P(name = "expression") String expression,
            @P(name = "timeoutMs", description = "Max wait in ms for the evaluation; 0 = default 10000.", required = false) Integer timeoutMs) {
        var session = DebugSession.findActive();
        if (session == null) {
            return noSession("debugJavaEvaluateExpression");
        }
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("expression must not be empty");
        }
        int timeout = timeoutMs == null || timeoutMs <= 0 ? 10000 : timeoutMs;
        IJavaStackFrame javaFrame = javaFrame(session, thread, frame);
        String projectName = session.sessionProject();
        if (projectName.isBlank()) {
            throw new IllegalArgumentException("cannot evaluate — the session has no project attribute, so the expression has no compilation context");
        }
        IJavaProject javaProject = JavaCore.create(project(projectName));
        if (!javaProject.exists()) {
            throw new IllegalArgumentException("project '" + projectName + "' is not a Java project — the expression cannot be compiled against it");
        }
        var result = new AtomicReference<IEvaluationResult>();
        var latch = new CountDownLatch(1);
        IAstEvaluationEngine engine = EvaluationManager.newAstEvaluationEngine(javaProject, session.target());
        try {
            try {
                engine.evaluate(expression, javaFrame, eval -> {
                    result.set(eval);
                    latch.countDown();
                }, DebugEvent.EVALUATION, false);
            } catch (DebugException e) {
                throw DebugSupport.fail("starting evaluation of '" + expression + "' in " + frameName(javaFrame), e);
            }
            if (!latch.await(timeout, TimeUnit.MILLISECONDS)) {
                try {
                    session.resolveThread(thread).terminateEvaluation();
                } catch (DebugException ignored) {
                    // best effort — the timeout is reported anyway
                }
                throw new IllegalArgumentException("evaluation timed out after " + timeout
                        + " ms — check the Debug view; the VM evaluation may have completed");
            }
            IEvaluationResult evaluationResult = result.get();
            if (evaluationResult == null) {
                throw new IllegalArgumentException("evaluation returned no result — check the Debug view; the VM evaluation may have completed");
            }
            if (evaluationResult.hasErrors()) {
                throw new IllegalArgumentException("evaluation failed: " + String.join("; ", evaluationResult.getErrorMessages()));
            }
            return DebugJson.evaluated(evaluationResult.getValue());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("evaluation of '" + expression + "' was interrupted");
        } finally {
            engine.dispose();
        }
    }

    @Tool(name = "debugJavaGetException", value = "Find the exception at the current suspend: scans the top frame's local variables (incl. catch parameter) for a java.lang.Throwable or subtype and returns its type, message and variable name. Recognition is by name — exact java.lang.Throwable or a simple name ending in Exception/Error; a custom Throwable subclass with an unusual name is NOT recognized (then use debugJavaGetVariables). Limit: an uncaught throw new X(...) at the throw site has no named variable and is not found there.")
    public String getException(@P(name = "thread", description = "Thread name; empty = first suspended thread, else first non-system thread.", required = false) String thread) {
        var session = DebugSession.findActive();
        if (session == null) {
            return noSession("debugJavaGetException");
        }
        return DebugJson.exception(session.resolveThread(thread));
    }

    @Tool(name = "debugJavaSetVariable", value = "Set a local variable or argument to a primitive, String or null value. No confirmation.")
    public String setVariable(@P(name = "thread", description = "Thread name; empty = first suspended thread, else first non-system thread.", required = false) String thread,
            @P(name = "frame", description = "Stack frame index, 0 = top.", required = false) Integer frame,
            @P(name = "name") String name,
            @P(name = "value", description = "New value as text: null, boolean, number, single char or string.") String value) {
        var session = DebugSession.findActive();
        if (session == null) {
            return noSession("debugJavaSetVariable");
        }
        IJavaStackFrame javaFrame = javaFrame(session, thread, frame);
        IJavaVariable variable;
        try {
            variable = javaFrame.findVariable(name);
        } catch (DebugException e) {
            throw DebugSupport.fail("reading variable " + name + " in frame " + frameName(javaFrame), e);
        }
        if (variable == null) {
            throw new IllegalArgumentException("variable '" + name + "' not visible in frame " + frameName(javaFrame)
                    + " (searched: local variables and arguments)");
        }
        IJavaValue newValue = newValueFor(session.target(), variable, value);
        try {
            variable.setValue(newValue);
        } catch (DebugException e) {
            throw DebugSupport.fail("setting variable " + name + " to " + value, e);
        }
        String type;
        IValue after;
        try {
            type = variable.getReferenceTypeName();
            after = variable.getValue();
        } catch (DebugException e) {
            throw DebugSupport.fail("reading the new value of variable " + name, e);
        }
        return DebugJson.valueResponse(name, type, after);
    }

    @Tool(name = "debugJavaSetBreakpoint", value = "Set a line breakpoint with optional condition, hit count and suspend policy (THREAD or VM).")
    public String setBreakpoint(@P(name = "file", description = "Project-relative file path of the session project (or /project/... absolute).") String file,
            @P(name = "line", description = "1-based line number.") int line,
            @P(name = "condition", description = "Breakpoint condition expression; empty = none.", required = false) String condition,
            @P(name = "hitCount", description = "Suspend after this many hits; 0 = every hit.", required = false) Integer hitCount,
            @P(name = "suspendPolicy", description = "THREAD (default) or VM.", required = false) String suspendPolicy) {
        var session = DebugSession.findActive();
        if (session == null) {
            return noSession("debugJavaSetBreakpoint");
        }
        if (line < 1) {
            throw new IllegalArgumentException("line must be >= 1 (got: " + line + ")");
        }
        int hits = hitCount == null ? 0 : hitCount;
        if (hits < 0) {
            throw new IllegalArgumentException("hitCount must be >= 0 (got: " + hits + ")");
        }
        IProject project;
        String relativePath;
        if (file != null && file.startsWith("/")) {
            var segments = file.substring(1).split("/");
            if (segments.length < 2 || segments[0].isBlank()) {
                throw new IllegalArgumentException("an absolute file must be /project/path (got: " + file + ")");
            }
            project = project(segments[0]);
            relativePath = String.join("/", Arrays.copyOfRange(segments, 1, segments.length));
        } else {
            String projectName = session.sessionProject();
            if (projectName.isBlank()) {
                throw new IllegalArgumentException("cannot resolve the project for file '" + file
                        + "' — the session has no project attribute, pass /project/path explicitly");
            }
            project = project(projectName);
            relativePath = file;
        }
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("no file path after the project segment in '" + file + "'");
        }
        IFile fileResource = project.getFile(relativePath);
        if (!fileResource.exists()) {
            throw new IllegalArgumentException("file '" + file + "' not found in project " + project.getName());
        }
        String typeName = primaryTypeName(JavaCore.create(project), fileResource, file);
        IJavaLineBreakpoint breakpoint;
        try {
            breakpoint = JDIDebugModel.createLineBreakpoint(fileResource, typeName, line, -1, -1, hits, true,
                    new HashMap<>());
        } catch (CoreException e) {
            throw DebugSupport.fail("creating a line breakpoint in " + file + ":" + line, e);
        }
        try {
            breakpoint.setSuspendPolicy(parseSuspendPolicy(suspendPolicy));
            if (condition != null && !condition.isBlank()) {
                breakpoint.setCondition(condition.trim());
            }
        } catch (CoreException e) {
            throw DebugSupport.fail("configuring the breakpoint in " + file + ":" + line, e);
        }
        return DebugJson.breakpointResponse(breakpoint, file, line, null);
    }

    @Tool(name = "debugJavaSetExceptionBreakpoint", value = "Set an exception breakpoint for a type with suspend policy and caught/uncaught/subtype options.")
    public String setExceptionBreakpoint(@P(name = "exceptionType", description = "Fully qualified exception type name.") String exceptionType,
            @P(name = "suspendPolicy", description = "THREAD (default) or VM.", required = false) String suspendPolicy,
            @P(name = "catchUncaught", description = "Catch uncaught exceptions; empty = true.", required = false) Boolean catchUncaught,
            @P(name = "catchCaught", description = "Catch caught exceptions; empty = false.", required = false) Boolean catchCaught,
            @P(name = "subTypes", description = "Include subtypes; empty = true.", required = false) Boolean subTypes) {
        var session = DebugSession.findActive();
        if (session == null) {
            return noSession("debugJavaSetExceptionBreakpoint");
        }
        if (exceptionType == null || exceptionType.isBlank()) {
            throw new IllegalArgumentException("exceptionType must be a fully qualified type name");
        }
        boolean uncaught = catchUncaught == null || catchUncaught;
        boolean caught = catchCaught != null && catchCaught;
        if (!uncaught && !caught) {
            throw new IllegalArgumentException("at least one of catchUncaught / catchCaught must be true");
        }
        if (subTypes != null && !subTypes) {
            throw new IllegalArgumentException("subTypes=false is not supported — a JDI exception breakpoint always matches the type and its subtypes");
        }
        IJavaExceptionBreakpoint breakpoint;
        try {
            breakpoint = JDIDebugModel.createExceptionBreakpoint(ResourcesPlugin.getWorkspace().getRoot(),
                    exceptionType, caught, uncaught, false, true, new HashMap<>());
        } catch (CoreException e) {
            throw DebugSupport.fail("creating an exception breakpoint for " + exceptionType, e);
        }
        try {
            breakpoint.setSuspendPolicy(parseSuspendPolicy(suspendPolicy));
        } catch (CoreException e) {
            throw DebugSupport.fail("configuring the exception breakpoint for " + exceptionType, e);
        }
        return DebugJson.breakpointResponse(breakpoint, null, null, exceptionType);
    }

    @Tool(name = "debugJavaRemoveBreakpoint", value = "Remove a breakpoint previously created by debugJavaSetBreakpoint or debugJavaSetExceptionBreakpoint, given its marker id.")
    public String removeBreakpoint(@P(name = "id", description = "Breakpoint marker id from a debugJavaSetBreakpoint response.") String id) {
        var session = DebugSession.findActive();
        if (session == null) {
            return noSession("debugJavaRemoveBreakpoint");
        }
        long markerId;
        try {
            markerId = Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("breakpoint id must be numeric (got: '" + id + "')");
        }
        IMarker marker = findMarkerById(markerId);
        if (marker == null || !marker.exists()) {
            throw new IllegalArgumentException("no breakpoint with marker id " + markerId + " — it was probably already removed");
        }
        var registered = DebugPlugin.getDefault().getBreakpointManager().getBreakpoint(marker);
        try {
            if (registered != null) {
                registered.delete();
            }
            if (marker.exists()) {
                marker.delete();
            }
        } catch (CoreException e) {
            throw DebugSupport.fail("removing breakpoint marker id " + markerId, e);
        }
        return DebugJson.removedResponse(markerId);
    }

    @Tool(name = "debugJavaListBreakpoints", value = "List the breakpoint map of the project selected in the chat view: all line breakpoints of that project plus the workspace-wide exception breakpoints (their markers live on the workspace root, so they appear for every project), each with id, type, location, condition, hit count and enabled state. Works without a debug session — it reads the persistent markers, so phantom breakpoints set in the UI between sessions are visible too. hitCount 0 = every hit. Removing a breakpoint stays debugJavaRemoveBreakpoint(id). No project selected → honest error.")
    public String listBreakpoints() {
        if (currentProject == null) {
            onProblem(NO_PROJECT);
            return NO_PROJECT;
        }
        return listBreakpoints(currentProject);
    }

    /**
     * R-JD-11 (UC-JD-13): the breakpoint map of the given project — the project's line
     * breakpoints plus the workspace-wide exception breakpoints (their markers live on the
     * workspace root). Deliberate exception to the R-JD-1 session guard: the markers
     * outlive the target, so the map works between sessions too. Public static test seam
     * (precedent: {@link #primaryTypeName}).
     */
    public static String listBreakpoints(IProject project) {
        var root = ResourcesPlugin.getWorkspace().getRoot();
        return DebugJson.breakpointListResponse(
                project.getName(),
                findMarkers(project, LINE_BREAKPOINT_MARKER, IResource.DEPTH_INFINITE),
                findMarkers(root, EXCEPTION_BREAKPOINT_MARKER, IResource.DEPTH_ZERO));
    }

    private static List<IMarker> findMarkers(IResource resource, String markerType, int depth) {
        try {
            return new ArrayList<>(List.of(resource.findMarkers(markerType, true, depth)));
        } catch (CoreException e) {
            throw DebugSupport.fail("scanning " + markerType + " markers of " + resource.getName(), e);
        }
    }

    @Tool(name = "debugJavaStepOver", value = "Step over in a debug thread and wait for the next suspend; returns the new top frame.")
    public String stepOver(@P(name = "thread", description = "Thread name; empty = first suspended thread, else first non-system thread.", required = false) String thread,
            @P(name = "waitMs", description = "Max wait in ms for the next suspend; 0 = default 15000.", required = false) Integer waitMs) {
        var session = DebugSession.findActive();
        if (session == null) {
            return noSession("debugJavaStepOver");
        }
        IJavaThread debugThread = session.resolveThread(thread);
        try {
            debugThread.stepOver();
        } catch (DebugException e) {
            throw DebugSupport.fail("stepping over in thread " + DebugSupport.threadName(debugThread), e);
        }
        return waitForSuspend(session, debugThread, "debugJavaStepOver", waitMs == null || waitMs <= 0 ? 15000 : waitMs);
    }

    @Tool(name = "debugJavaStepIn", value = "Step into a debug thread and wait for the next suspend; returns the new top frame.")
    public String stepIn(@P(name = "thread", description = "Thread name; empty = first suspended thread, else first non-system thread.", required = false) String thread,
            @P(name = "waitMs", description = "Max wait in ms for the next suspend; 0 = default 15000.", required = false) Integer waitMs) {
        var session = DebugSession.findActive();
        if (session == null) {
            return noSession("debugJavaStepIn");
        }
        IJavaThread debugThread = session.resolveThread(thread);
        try {
            debugThread.stepInto();
        } catch (DebugException e) {
            throw DebugSupport.fail("stepping into a method in thread " + DebugSupport.threadName(debugThread), e);
        }
        return waitForSuspend(session, debugThread, "debugJavaStepIn", waitMs == null || waitMs <= 0 ? 15000 : waitMs);
    }

    @Tool(name = "debugJavaStepOut", value = "Step out of the current frame and wait for the next suspend; returns the new top frame.")
    public String stepOut(@P(name = "thread", description = "Thread name; empty = first suspended thread, else first non-system thread.", required = false) String thread,
            @P(name = "waitMs", description = "Max wait in ms for the next suspend; 0 = default 15000.", required = false) Integer waitMs) {
        var session = DebugSession.findActive();
        if (session == null) {
            return noSession("debugJavaStepOut");
        }
        IJavaThread debugThread = session.resolveThread(thread);
        IStackFrame[] frames;
        try {
            frames = debugThread.getStackFrames();
        } catch (DebugException e) {
            throw DebugSupport.fail("reading stack frames of thread " + DebugSupport.threadName(debugThread), e);
        }
        if (frames.length <= 1) {
            throw new IllegalArgumentException("thread " + DebugSupport.threadName(debugThread) + " is already at its top frame — use debugJavaContinue");
        }
        try {
            debugThread.stepReturn();
        } catch (DebugException e) {
            throw DebugSupport.fail("stepping out in thread " + DebugSupport.threadName(debugThread), e);
        }
        return waitForSuspend(session, debugThread, "debugJavaStepOut", waitMs == null || waitMs <= 0 ? 15000 : waitMs);
    }

    @Tool(name = "debugJavaContinue", value = "Resume a suspended debug thread and wait for the next suspend; returns the new top frame.")
    public String resume(@P(name = "thread", description = "Thread name; empty = first suspended thread, else first non-system thread.", required = false) String thread,
            @P(name = "waitMs", description = "Max wait in ms for the next suspend; 0 = default 30000.", required = false) Integer waitMs) {
        var session = DebugSession.findActive();
        if (session == null) {
            return noSession("debugJavaContinue");
        }
        IJavaThread debugThread = session.resolveThread(thread);
        if (!debugThread.isSuspended()) {
            throw new IllegalArgumentException("thread " + DebugSupport.threadName(debugThread) + " is not suspended — nothing to continue");
        }
        try {
            debugThread.resume();
        } catch (DebugException e) {
            throw DebugSupport.fail("resuming thread " + DebugSupport.threadName(debugThread), e);
        }
        return waitForSuspend(session, debugThread, "debugJavaContinue", waitMs == null || waitMs <= 0 ? 30000 : waitMs);
    }

    @Tool(name = "debugJavaSuspend", value = "Suspend a running debug session; returns the suspended threads with their top frames.")
    public String suspend() {
        var session = DebugSession.findActive();
        if (session == null) {
            return noSession("debugJavaSuspend");
        }
        try {
            session.target().suspend();
        } catch (DebugException e) {
            throw DebugSupport.fail("suspending the VM " + session.vmName(), e);
        }
        return DebugJson.state(session);
    }

    private static IJavaStackFrame javaFrame(DebugSession session, String thread, Integer frameIndex) {
        int index = frameIndex == null ? 0 : frameIndex;
        var debugThread = session.resolveThread(thread);
        var stackFrame = session.resolveFrame(debugThread, index);
        if (!(stackFrame instanceof IJavaStackFrame javaFrame)) {
            throw new IllegalArgumentException("frame " + index + " of the resolved thread is not a Java frame");
        }
        return javaFrame;
    }

    private static String frameName(IJavaStackFrame frame) {
        try {
            return frame.getMethodName() + " in " + frame.getDeclaringTypeName();
        } catch (DebugException e) {
            return "<unknown frame>";
        }
    }

    /** D7: the value parser — primitives, String or null, strictly against the declared type. */
    private static IJavaValue newValueFor(IJavaDebugTarget target, IJavaVariable variable, String text) {
        String declared;
        try {
            declared = variable.getReferenceTypeName();
        } catch (DebugException e) {
            declared = "";
        }
        if ("null".equals(text)) {
            if (!"java.lang.String".equals(declared)) {
                throw new IllegalArgumentException("debugJavaSetVariable: null is only allowed for a java.lang.String variable (declared: " + declared + ")");
            }
            return target.nullValue();
        }
        return switch (declared) {
            case "boolean" -> target.newValue(parseBoolean(text));
            case "byte" -> target.newValue((byte) parseBounded(text, declared, Byte.MIN_VALUE, Byte.MAX_VALUE));
            case "short" -> target.newValue((short) parseBounded(text, declared, Short.MIN_VALUE, Short.MAX_VALUE));
            case "int" -> target.newValue((int) parseBounded(text, declared, Integer.MIN_VALUE, Integer.MAX_VALUE));
            case "long" -> target.newValue(parseBounded(text, declared, Long.MIN_VALUE, Long.MAX_VALUE));
            case "float" -> target.newValue(parseFloat(text));
            case "double" -> target.newValue(parseDouble(text));
            case "char" -> {
                if (text.length() != 1) {
                    throw new IllegalArgumentException("debugJavaSetVariable: '" + text + "' is not a single char");
                }
                yield target.newValue(text.charAt(0));
            }
            case "java.lang.String" -> target.newValue(text);
            default -> throw new IllegalArgumentException("debugJavaSetVariable targets primitives, String or null only (declared: " + declared + ")");
        };
    }

    private static boolean parseBoolean(String text) {
        if ("true".equals(text)) {
            return true;
        }
        if ("false".equals(text)) {
            return false;
        }
        throw new IllegalArgumentException("debugJavaSetVariable: '" + text + "' is not a boolean (expected true or false)");
    }

    private static long parseBounded(String text, String declared, long min, long max) {
        try {
            long value = Long.parseLong(text.trim());
            if (value < min || value > max) {
                throw new IllegalArgumentException("debugJavaSetVariable: " + text + " is out of range for " + declared + " (" + min + "…" + max + ")");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("debugJavaSetVariable: '" + text + "' is not a " + declared);
        }
    }

    private static float parseFloat(String text) {
        try {
            return Float.parseFloat(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("debugJavaSetVariable: '" + text + "' is not a float");
        }
    }

    private static double parseDouble(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("debugJavaSetVariable: '" + text + "' is not a double");
        }
    }

    /** Marker lookup by id — a workspace-wide scan of generic markers (the Markers.findMarkerById API is gone). */
    private static IMarker findMarkerById(long markerId) {
        try {
            return Arrays.stream(ResourcesPlugin.getWorkspace().getRoot().findMarkers(IMarker.MARKER, true, IResource.DEPTH_INFINITE))
                    .filter(marker -> marker.getId() == markerId)
                    .findFirst()
                    .orElse(null);
        } catch (CoreException e) {
            throw DebugSupport.fail("scanning the workspace for marker id " + markerId, e);
        }
    }

    private static IProject project(String name) {
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
        if (!project.exists()) {
            throw new IllegalArgumentException("project '" + name + "' does not exist in the workspace");
        }
        return project;
    }

    /**
     * Resolves the file to the primary type of its compilation unit via the JDT
     * model — JavaCore.createCompilationUnitFrom(IFile) → ICompilationUnit →
     * findPrimaryType (2026-09-21 E2E F1: the raw IFile was not loaded, so every
     * debugJavaSetBreakpoint failed with "not a Java compilation unit").
     *
     * @param fileLabel the file as the user passed it (for honest errors)
     * @throws IllegalArgumentException when the file is not a Java source file with a primary type
     */
    public static String primaryTypeName(IJavaProject javaProject, IFile file, String fileLabel) {
        return primaryTypeName(JavaCore.createCompilationUnitFrom(file), javaProject.getElementName(), fileLabel);
    }

    public static String primaryTypeName(ICompilationUnit unit, String projectName, String fileLabel) {
        if (unit == null) {
            throw new IllegalArgumentException("file '" + fileLabel + "' in project " + projectName
                    + " is not a Java source file — only .java files in a source folder can take line breakpoints");
        }
        IType primaryType = unit.findPrimaryType();
        if (primaryType == null) {
            throw new IllegalArgumentException("file '" + fileLabel + "' in project " + projectName
                    + " has no primary Java type");
        }
        return primaryType.getFullyQualifiedName();
    }

    private static int parseSuspendPolicy(String policy) {
        if (policy == null || policy.isBlank()) {
            return IJavaBreakpoint.SUSPEND_THREAD;
        }
        return switch (policy.trim().toUpperCase()) {
            case "THREAD" -> IJavaBreakpoint.SUSPEND_THREAD;
            case "VM" -> IJavaBreakpoint.SUSPEND_VM;
            default -> throw new IllegalArgumentException("suspendPolicy must be THREAD or VM (got: " + policy + ")");
        };
    }

    private String noSession(String action) {
        onProblem(action + ": " + DebugSession.NO_SESSION);
        return DebugSession.NO_SESSION;
    }

    /**
     * D9: steps and resume are non-blocking on the JDI side — wait for the resolved thread's
     * next suspend (100 ms tick, hard deadline) and answer with its new top frame.
     * Every edge is reported honestly: still suspended, VM terminated, still running.
     */
    private static String waitForSuspend(DebugSession session, IJavaThread debugThread, String action, int waitMs) {
        long deadline = System.currentTimeMillis() + waitMs;
        while (debugThread.isSuspended() && System.currentTimeMillis() < deadline) {
            sleep(100);
        }
        if (debugThread.isSuspended()) {
            throw new IllegalArgumentException(action + ": thread " + DebugSupport.threadName(debugThread) + " is still suspended after "
                    + waitMs + " ms — check the Debug view");
        }
        while (System.currentTimeMillis() < deadline) {
            if (session.target().isTerminated()) {
                throw new IllegalArgumentException(action + ": the VM terminated while waiting — check the Debug view");
            }
            if (debugThread.isSuspended()) {
                return DebugJson.controlResponse(debugThread);
            }
            sleep(100);
        }
        throw new IllegalArgumentException(action + ": still running after " + waitMs + " ms — no breakpoint hit?");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("waiting for the next suspend was interrupted");
        }
    }

}
