package org.sterl.llmpeon.parts.tools.debug;

import org.sterl.llmpeon.tool.tools.AbstractTool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * Reads and drives a user-started Java debug session (JDT debug model, R-JD-4):
 * state, stack, variables, expression evaluation, variables, breakpoints and steps.
 * Stateless — every call re-resolves the session (R-JD-3). Never auto-starts and
 * never auto-disconnects a session (R-JD-1): without an active session every
 * action answers with the honest no-session message.
 *
 * Optional numeric parameters follow "0 = unset" (D3).
 */
public class JavaDebugTool extends AbstractTool {

    @Override
    public boolean isEditTool() { return true; }

    @Tool(name = "get_state", value = "Show the active Java debug session: VM state, all threads with state, system flag and top frame.")
    public String getState() {
        var session = DebugSession.findActive();
        if (session == null) {
            return noSession("get_state");
        }
        return notYetAvailable("get_state");
    }

    @Tool(name = "get_stack_trace", value = "List the stack frames of a debug thread (index, method, type, line, method entry). Optional thread name.")
    public String getStackTrace(@P(name = "thread", description = "Thread name; empty = first suspended thread, else first non-system thread.", required = false) String thread) {
        var session = DebugSession.findActive();
        if (session == null) {
            return noSession("get_stack_trace");
        }
        return notYetAvailable("get_stack_trace");
    }

    @Tool(name = "get_variables", value = "Show variables of a stack frame as JSON. Optional name path (a.b.c) and depth (default 1, max 5).")
    public String getVariables(@P(name = "thread", description = "Thread name; empty = first suspended thread, else first non-system thread.", required = false) String thread,
            @P(name = "frame", description = "Stack frame index, 0 = top.", required = false) int frame,
            @P(name = "name", description = "Variable path (a.b.c) to drill into; empty = all top-level variables.", required = false) String name,
            @P(name = "depth", description = "Nesting depth for object fields, 1..5; 0 = default 1.", required = false) int depth) {
        var session = DebugSession.findActive();
        if (session == null) {
            return noSession("get_variables");
        }
        return notYetAvailable("get_variables");
    }

    @Tool(name = "evaluate_expression", value = "Evaluate a Java expression in a suspended stack frame and return the result as JSON.")
    public String evaluateExpression(@P(name = "thread", description = "Thread name; empty = first suspended thread, else first non-system thread.", required = false) String thread,
            @P(name = "frame", description = "Stack frame index, 0 = top.", required = false) int frame,
            @P(name = "expression") String expression,
            @P(name = "timeoutMs", description = "Max wait in ms for the evaluation; 0 = default 10000.", required = false) int timeoutMs) {
        var session = DebugSession.findActive();
        if (session == null) {
            return noSession("evaluate_expression");
        }
        return notYetAvailable("evaluate_expression");
    }

    @Tool(name = "set_variable", value = "Set a local variable or argument to a primitive, String or null value. No confirmation.")
    public String setVariable(@P(name = "thread", description = "Thread name; empty = first suspended thread, else first non-system thread.", required = false) String thread,
            @P(name = "frame", description = "Stack frame index, 0 = top.", required = false) int frame,
            @P(name = "name") String name,
            @P(name = "value", description = "New value as text: null, boolean, number, single char or string.") String value) {
        var session = DebugSession.findActive();
        if (session == null) {
            return noSession("set_variable");
        }
        return notYetAvailable("set_variable");
    }

    @Tool(name = "set_breakpoint", value = "Set a line breakpoint with optional condition, hit count and suspend policy (THREAD or VM).")
    public String setBreakpoint(@P(name = "file", description = "Project-relative file path of the session project (or /project/... absolute).") String file,
            @P(name = "line", description = "1-based line number.") int line,
            @P(name = "condition", description = "Breakpoint condition expression; empty = none.", required = false) String condition,
            @P(name = "hitCount", description = "Suspend after this many hits; 0 = every hit.", required = false) int hitCount,
            @P(name = "suspendPolicy", description = "THREAD (default) or VM.", required = false) String suspendPolicy) {
        var session = DebugSession.findActive();
        if (session == null) {
            return noSession("set_breakpoint");
        }
        return notYetAvailable("set_breakpoint");
    }

    @Tool(name = "set_exception_breakpoint", value = "Set an exception breakpoint for a type with suspend policy and caught/uncaught/subtype options.")
    public String setExceptionBreakpoint(@P(name = "exceptionType", description = "Fully qualified exception type name.") String exceptionType,
            @P(name = "suspendPolicy", description = "THREAD (default) or VM.", required = false) String suspendPolicy,
            @P(name = "catchUncaught", description = "Catch uncaught exceptions; empty = true.", required = false) Boolean catchUncaught,
            @P(name = "catchCaught", description = "Catch caught exceptions; empty = false.", required = false) Boolean catchCaught,
            @P(name = "subTypes", description = "Include subtypes; empty = true.", required = false) Boolean subTypes) {
        var session = DebugSession.findActive();
        if (session == null) {
            return noSession("set_exception_breakpoint");
        }
        return notYetAvailable("set_exception_breakpoint");
    }

    @Tool(name = "remove_breakpoint", value = "Remove a breakpoint previously created by set_breakpoint or set_exception_breakpoint, given its marker id.")
    public String removeBreakpoint(@P(name = "id", description = "Breakpoint marker id from a set_breakpoint response.") String id) {
        var session = DebugSession.findActive();
        if (session == null) {
            return noSession("remove_breakpoint");
        }
        return notYetAvailable("remove_breakpoint");
    }

    @Tool(name = "step_over", value = "Step over in a debug thread and wait for the next suspend; returns the new top frame.")
    public String stepOver(@P(name = "thread", description = "Thread name; empty = first suspended thread, else first non-system thread.", required = false) String thread,
            @P(name = "waitMs", description = "Max wait in ms for the next suspend; 0 = default 15000.", required = false) int waitMs) {
        var session = DebugSession.findActive();
        if (session == null) {
            return noSession("step_over");
        }
        return notYetAvailable("step_over");
    }

    @Tool(name = "step_in", value = "Step into a debug thread and wait for the next suspend; returns the new top frame.")
    public String stepIn(@P(name = "thread", description = "Thread name; empty = first suspended thread, else first non-system thread.", required = false) String thread,
            @P(name = "waitMs", description = "Max wait in ms for the next suspend; 0 = default 15000.", required = false) int waitMs) {
        var session = DebugSession.findActive();
        if (session == null) {
            return noSession("step_in");
        }
        return notYetAvailable("step_in");
    }

    @Tool(name = "step_out", value = "Step out of the current frame and wait for the next suspend; returns the new top frame.")
    public String stepOut(@P(name = "thread", description = "Thread name; empty = first suspended thread, else first non-system thread.", required = false) String thread,
            @P(name = "waitMs", description = "Max wait in ms for the next suspend; 0 = default 15000.", required = false) int waitMs) {
        var session = DebugSession.findActive();
        if (session == null) {
            return noSession("step_out");
        }
        return notYetAvailable("step_out");
    }

    @Tool(name = "continue", value = "Resume a suspended debug thread and wait for the next suspend; returns the new top frame.")
    public String resume(@P(name = "thread", description = "Thread name; empty = first suspended thread, else first non-system thread.", required = false) String thread,
            @P(name = "waitMs", description = "Max wait in ms for the next suspend; 0 = default 30000.", required = false) int waitMs) {
        var session = DebugSession.findActive();
        if (session == null) {
            return noSession("continue");
        }
        return notYetAvailable("continue");
    }

    @Tool(name = "suspend", value = "Suspend a running debug session; returns the suspended threads with their top frames.")
    public String suspend() {
        var session = DebugSession.findActive();
        if (session == null) {
            return noSession("suspend");
        }
        return notYetAvailable("suspend");
    }

    private String noSession(String action) {
        onProblem(action + ": " + DebugSession.NO_SESSION);
        return DebugSession.NO_SESSION;
    }

    /** I1 scaffold: actions without a session answer honestly, with a session they are not implemented yet. */
    private static String notYetAvailable(String action) {
        throw new IllegalArgumentException(action + " not yet available in this build");
    }
}
