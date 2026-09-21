package org.sterl.llmpeon.parts.tools.debug;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaBreakpoint;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaExceptionBreakpoint;
import org.eclipse.jdt.debug.core.IJavaFieldVariable;
import org.eclipse.jdt.debug.core.IJavaLineBreakpoint;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaPrimitiveValue;
import org.eclipse.jdt.debug.core.IJavaReferenceType;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.core.IJavaVariable;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Pure functions rendering the Eclipse debug model as pretty JSON (D5).
 * Stateless — the shared {@link ObjectMapper} is the only static field.
 * Every limit is named in the output (array element cap).
 */
public final class DebugJson {

    private static final int ARRAY_ELEMENT_LIMIT = 20;
    private static final int MAX_DEPTH = 5;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DebugJson() {
    }

    public static String pretty(Object node) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to render debug JSON: " + e.getMessage(), e);
        }
    }

    /**
     * get_state shape: { session, vm: {name, version, state, suspendedThreads, outOfSynch},
     * threads: [{name, state, system, topFrame}] }.
     * vm.state = "suspended" when the target is suspended OR any non-system thread is
     * suspended (the debugging-relevant "where is it standing" question); suspendedThreads
     * counts the suspended non-system threads and names the mixed state honestly
     * (e.g. main suspended, system threads running).
     */
    public static String state(DebugSession session) {
        IJavaDebugTarget target = session.target();
        var threads = session.threads();
        int suspendedNonSystem = 0;
        for (IJavaThread thread : threads) {
            if (!DebugSupport.isSystem(thread) && thread.isSuspended()) {
                suspendedNonSystem++;
            }
        }
        Map<String, Object> vm = new LinkedHashMap<>();
        vm.put("name", session.vmName());
        vm.put("version", session.vmVersion());
        vm.put("state", (isSuspended(target) || suspendedNonSystem > 0) ? "suspended" : "running");
        vm.put("suspendedThreads", suspendedNonSystem);
        vm.put("outOfSynch", outOfSynch(target));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("session", sessionLabel(session));
        root.put("vm", vm);
        root.put("threads", threadNodes(threads));
        return pretty(root);
    }

    /** Session identity: launch config name + process id (pid omitted when unavailable). */
    private static String sessionLabel(DebugSession session) {
        Integer pid = session.processId();
        return pid == null ? session.launchName() : session.launchName() + " (pid " + pid + ")";
    }

    /** get_stack_trace shape: [{index, method, type, line, methodEntry}]. */
    static String stackTrace(IJavaThread thread) {
        IStackFrame[] frames;
        try {
            frames = thread.getStackFrames();
        } catch (DebugException e) {
            throw DebugSupport.fail("reading stack frames of thread " + DebugSupport.threadName(thread), e);
        }
        var nodes = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < frames.length; i++) {
            IStackFrame frame = frames[i];
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("index", i);
            node.put("method", frameName(frame));
            node.put("type", declaringType(frame));
            node.put("line", line(frame));
            node.put("methodEntry", isMethodEntry(frame));
            nodes.add(node);
        }
        return pretty(nodes);
    }

    /**
     * get_variables shape: { locals: […], statics: […] } without a name path — each variable
     * as {name, type, value | fields | length+elements}; the statics block holds the static
     * fields of the frame's declaring type, empty when there are none (R-JD-9).
     * {@code namePath} drills into nested variables (a.b.c) and renders a single node;
     * {@code depth} bounds field nesting (1..5, 0 = 1).
     */
    public static String variables(IJavaStackFrame frame, String namePath, int depth) {
        int d = depth <= 0 ? 1 : Math.min(depth, MAX_DEPTH);
        IJavaVariable[] locals;
        try {
            locals = frame.getLocalVariables();
        } catch (DebugException e) {
            throw DebugSupport.fail("reading local variables of frame " + frameName(frame), e);
        }
        if (namePath == null || namePath.isBlank()) {
            Map<String, Object> root = new LinkedHashMap<>();
            var localsNodes = new ArrayList<Map<String, Object>>();
            for (IJavaVariable variable : locals) {
                localsNodes.add(variableNode(variable, d));
            }
            root.put("locals", localsNodes);
            root.put("statics", staticNodes(frame, d));
            return pretty(root);
        }
        IVariable current = null;
        List<IVariable> scope = new ArrayList<>(List.of(locals));
        var segments = namePath.split("\\.");
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            IVariable match = null;
            for (IVariable variable : scope) {
                if (segment.equals(safeName(variable))) {
                    match = variable;
                    break;
                }
            }
            if (match == null) {
                throw new IllegalArgumentException("path segment '" + segment + "' not found — available: "
                        + names(scope) + " (scope: "
                        + (current == null ? "frame locals" : "fields of " + safeName(current)) + ")");
            }
            current = match;
            if (i < segments.length - 1) {
                scope = childrenOf(match);
            }
        }
        return pretty(variableNode(current, d));
    }

    /**
     * The static fields of the frame's declaring type, rendered like locals (R-JD-9);
     * empty when the frame has no resolvable declaring type or no static fields.
     */
    private static List<Map<String, Object>> staticNodes(IJavaStackFrame frame, int depth) {
        var statics = new ArrayList<Map<String, Object>>();
        IJavaReferenceType type;
        try {
            type = frame.getReferenceType();
        } catch (DebugException e) {
            throw DebugSupport.fail("reading the declaring type of frame " + frameName(frame), e);
        }
        if (type == null) {
            return statics;
        }
        String typeName = typeName(type);
        String[] fieldNames;
        try {
            fieldNames = type.getAllFieldNames();
        } catch (DebugException e) {
            throw DebugSupport.fail("reading the fields of " + typeName, e);
        }
        for (String name : fieldNames) {
            IJavaFieldVariable field;
            try {
                field = type.getField(name);
            } catch (DebugException e) {
                throw DebugSupport.fail("reading field " + name + " of " + typeName, e);
            }
            if (field == null) {
                continue;
            }
            try {
                if (!field.isStatic()) {
                    continue;
                }
            } catch (DebugException e) {
                throw DebugSupport.fail("reading the modifiers of field " + name + " of " + typeName, e);
            }
            statics.add(variableNode(field, depth));
        }
        return statics;
    }

    private static String typeName(IJavaReferenceType type) {
        try {
            return type.getName();
        } catch (DebugException e) {
            return "<unknown type>";
        }
    }

    /**
     * set_variable response: {name, type, value} (the new value). Primitives are
     * rendered as JSON primitives (boolean/number) like get_variables (2026-09-21
     * E2E F3); objects and strings as their value string, null as JSON null.
     */
    public static String valueResponse(String name, String type, IValue value) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("name", name);
        node.put("type", type);
        if (value == null || isNull(value)) {
            node.put("value", null);
        } else if (value instanceof IJavaPrimitiveValue primitive) {
            node.put("value", primitiveValue(primitive));
        } else {
            node.put("value", valueString(value));
        }
        return pretty(node);
    }

    /** set_breakpoint / set_exception_breakpoint response (D8). */
    static String breakpointResponse(IJavaBreakpoint breakpoint, String file, Integer line, String exceptionType) {
        Map<String, Object> node = new LinkedHashMap<>();
        try {
            node.put("id", String.valueOf(breakpoint.getMarker().getId()));
            node.put("type", breakpoint instanceof IJavaExceptionBreakpoint ? "exception" : "line");
            if (file != null) {
                node.put("file", file);
            }
            if (line != null) {
                node.put("line", line);
            }
            if (exceptionType != null) {
                node.put("exceptionType", exceptionType);
            }
            if (breakpoint instanceof IJavaLineBreakpoint lineBreakpoint) {
                node.put("condition", lineBreakpoint.getCondition());
            }
            node.put("hitCount", breakpoint.getHitCount());
            node.put("suspendPolicy", breakpoint.getSuspendPolicy() == IJavaBreakpoint.SUSPEND_VM ? "VM" : "THREAD");
            node.put("installed", breakpoint.isInstalled());
        } catch (CoreException e) {
            throw new IllegalArgumentException("reading the created breakpoint failed: " + e.getMessage(), e);
        }
        return pretty(node);
    }

    /** remove_breakpoint response: {id, removed}. */
    static String removedResponse(long id) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", String.valueOf(id));
        node.put("removed", true);
        return pretty(node);
    }

    /** step/continue response: {thread, state, topFrame} (the new top frame after the suspend). */
    static String controlResponse(IJavaThread thread) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("thread", DebugSupport.threadName(thread));
        node.put("state", isSuspended(thread) ? "suspended" : "running");
        node.put("topFrame", topFrame(thread));
        return pretty(node);
    }

    /**
     * evaluate_expression response: {type, value | fields | length+elements}. Object results
     * render their fields to depth 2 (R-JD-9); primitives, String and null come back as values;
     * arrays are named with length + capped elements.
     */
    public static String evaluated(IJavaValue value) {
        Map<String, Object> node = new LinkedHashMap<>();
        if (value == null || isNull(value)) {
            node.put("type", "null");
            node.put("value", null);
            return pretty(node);
        }
        node.put("type", valueTypeName(value));
        if (value instanceof IJavaPrimitiveValue primitive) {
            node.put("value", primitiveValue(primitive));
            return pretty(node);
        }
        if (value instanceof IJavaArray array) {
            node.put("length", arrayLength(array));
            node.put("elements", arrayElements(array, 1));
            return pretty(node);
        }
        applyValue(node, value, 2);
        return pretty(node);
    }

    /**
     * get_exception response: {varName, type, message}. Scans the top frame's local
     * variables (incl. the catch parameter) for a java.lang.Throwable or subtype
     * (R-JD-10); the message comes from invoking getMessage(), null when unreadable.
     * Limit: an uncaught throw new X(...) at the throw site has no named variable.
     */
    public static String exception(IJavaThread thread) {
        IStackFrame topFrame;
        try {
            topFrame = thread.getTopStackFrame();
        } catch (DebugException e) {
            throw DebugSupport.fail("reading the top frame of thread " + DebugSupport.threadName(thread), e);
        }
        if (topFrame == null) {
            throw new IllegalArgumentException("no top frame — thread " + DebugSupport.threadName(thread) + " has no stack");
        }
        if (!(topFrame instanceof IJavaStackFrame javaFrame)) {
            throw new IllegalArgumentException("top frame of thread " + DebugSupport.threadName(thread) + " is not a Java frame");
        }
        IJavaVariable[] locals;
        try {
            locals = javaFrame.getLocalVariables();
        } catch (DebugException e) {
            throw DebugSupport.fail("reading local variables of frame " + frameName(topFrame), e);
        }
        for (IJavaVariable variable : locals) {
            IValue value;
            try {
                value = variable.getValue();
            } catch (DebugException e) {
                throw DebugSupport.fail("reading value of variable " + safeName(variable), e);
            }
            if (!(value instanceof IJavaObject object) || object.isNull()) {
                continue;
            }
            IJavaType type;
            try {
                type = object.getJavaType();
            } catch (DebugException e) {
                throw DebugSupport.fail("reading the type of variable " + safeName(variable), e);
            }
            if (type == null || !isThrowable(type)) {
                continue;
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("varName", safeName(variable));
            node.put("type", valueTypeName(object));
            node.put("message", messageOf(object, thread));
            return pretty(node);
        }
        throw new IllegalArgumentException("no exception variable in top frame " + frameName(topFrame));
    }

    /** Name-based throwable check: java.lang.Throwable itself, or a simple name ending in Exception/Error. */
    private static boolean isThrowable(IJavaType type) {
        String name;
        try {
            name = type.getName();
        } catch (DebugException e) {
            return false;
        }
        if ("java.lang.Throwable".equals(name)) {
            return true;
        }
        String simple = name.substring(name.lastIndexOf('.') + 1);
        return simple.endsWith("Exception") || simple.endsWith("Error");
    }

    /** The exception message via getMessage(); null when the call or the result is unreadable. */
    private static String messageOf(IJavaObject object, IJavaThread thread) {
        IValue result;
        try {
            result = object.sendMessage("getMessage", "()Ljava/lang/String;", null, thread, false);
        } catch (DebugException e) {
            return null;
        }
        if (result == null || isNull(result)) {
            return null;
        }
        try {
            return result.getValueString();
        } catch (DebugException e) {
            return null;
        }
    }

    private static String valueTypeName(IValue value) {
        try {
            return value.getReferenceTypeName();
        } catch (DebugException e) {
            return "<unknown type>";
        }
    }

    private static List<Map<String, Object>> threadNodes(List<IJavaThread> threads) {
        var nodes = new ArrayList<Map<String, Object>>();
        for (IJavaThread thread : threads) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("name", DebugSupport.threadName(thread));
            node.put("state", isSuspended(thread) ? "suspended" : "running");
            node.put("system", DebugSupport.isSystem(thread));
            node.put("topFrame", topFrame(thread));
            nodes.add(node);
        }
        return nodes;
    }

    private static Map<String, Object> topFrame(IJavaThread thread) {
        IStackFrame frame;
        try {
            frame = thread.getTopStackFrame();
        } catch (DebugException e) {
            return null;
        }
        if (frame == null) {
            return null;
        }
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("method", frameName(frame));
        node.put("type", declaringType(frame));
        node.put("line", line(frame));
        return node;
    }

    private static Map<String, Object> variableNode(IVariable variable, int depth) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("name", safeName(variable));
        String type;
        try {
            type = variable.getReferenceTypeName();
        } catch (DebugException e) {
            type = "";
        }
        node.put("type", type);
        IValue value;
        try {
            value = variable.getValue();
        } catch (DebugException e) {
            throw DebugSupport.fail("reading value of variable " + safeName(variable), e);
        }
        applyValue(node, value, depth);
        return node;
    }

    /** Merges the value part (value | fields | length+elements) into the node. */
    private static void applyValue(Map<String, Object> node, IValue value, int depth) {
        if (value == null || isNull(value)) {
            node.put("value", null);
            return;
        }
        if (value instanceof IJavaPrimitiveValue primitive) {
            node.put("value", primitiveValue(primitive));
            return;
        }
        if (value instanceof IJavaArray array) {
            int length = arrayLength(array);
            node.put("length", length);
            node.put("elements", arrayElements(array, depth));
            return;
        }
        if (depth >= 2 && hasVariables(value)) {
            var fields = new ArrayList<Map<String, Object>>();
            for (IVariable child : childrenOf(value)) {
                fields.add(variableNode(child, depth - 1));
            }
            node.put("fields", fields);
            return;
        }
        node.put("value", valueString(value));
    }

    private static List<Object> arrayElements(IJavaArray array, int depth) {
        int length = arrayLength(array);
        var elements = new ArrayList<Object>();
        int shown = Math.min(length, ARRAY_ELEMENT_LIMIT);
        for (int i = 0; i < shown; i++) {
            IValue element;
            try {
                element = array.getValue(i);
            } catch (DebugException e) {
                throw DebugSupport.fail("reading element " + i + " of array " + arrayTypeName(array), e);
            }
            elements.add(elementNode(element, depth));
        }
        if (length > shown) {
            elements.add("… " + (length - shown) + " more (limit " + ARRAY_ELEMENT_LIMIT + " elements)");
        }
        return elements;
    }

    private static Object elementNode(IValue value, int depth) {
        if (value == null || isNull(value)) {
            return null;
        }
        if (value instanceof IJavaPrimitiveValue primitive) {
            return primitiveValue(primitive);
        }
        Map<String, Object> node = new LinkedHashMap<>();
        applyValue(node, value, depth);
        return node;
    }

    private static Object primitiveValue(IJavaPrimitiveValue value) {
        String type;
        try {
            type = value.getJavaType().getName();
        } catch (DebugException e) {
            return valueString(value);
        }
        return switch (type) {
            case "boolean" -> value.getBooleanValue();
            case "byte" -> value.getByteValue();
            case "short" -> value.getShortValue();
            case "int" -> value.getIntValue();
            case "long" -> value.getLongValue();
            case "float" -> value.getFloatValue();
            case "double" -> value.getDoubleValue();
            case "char" -> String.valueOf(value.getCharValue());
            default -> valueString(value);
        };
    }

    private static List<IVariable> childrenOf(IVariable variable) {
        IValue value;
        try {
            value = variable.getValue();
        } catch (DebugException e) {
            throw DebugSupport.fail("reading value of variable " + safeName(variable), e);
        }
        if (value == null || isNull(value)) {
            throw new IllegalArgumentException("variable '" + safeName(variable) + "' is null — no fields or elements to walk into");
        }
        try {
            if (!value.hasVariables()) {
                throw new IllegalArgumentException("variable '" + safeName(variable) + "' has no fields or elements");
            }
            return new ArrayList<>(List.of(value.getVariables()));
        } catch (DebugException e) {
            throw DebugSupport.fail("reading fields of variable " + safeName(variable), e);
        }
    }

    private static List<IVariable> childrenOf(IValue value) {
        try {
            return new ArrayList<>(List.of(value.getVariables()));
        } catch (DebugException e) {
            throw DebugSupport.fail("reading fields of " + valueString(value), e);
        }
    }

    private static boolean hasVariables(IValue value) {
        try {
            return value.hasVariables();
        } catch (DebugException e) {
            return false;
        }
    }

    private static int arrayLength(IJavaArray array) {
        try {
            return array.getLength();
        } catch (DebugException e) {
            throw DebugSupport.fail("reading length of array " + arrayTypeName(array), e);
        }
    }

    private static String arrayTypeName(IJavaArray array) {
        try {
            return array.getReferenceTypeName();
        } catch (DebugException e) {
            return "<array>";
        }
    }

    private static String valueString(IValue value) {
        try {
            return value.getValueString();
        } catch (DebugException e) {
            return "<unreadable: " + e.getMessage() + ">";
        }
    }

    private static boolean isNull(IValue value) {
        return value instanceof IJavaValue javaValue && javaValue.isNull();
    }

    private static String safeName(IVariable variable) {
        try {
            return variable.getName();
        } catch (DebugException e) {
            return "<unknown variable>";
        }
    }

    private static String names(List<IVariable> variables) {
        var names = new ArrayList<String>();
        for (IVariable variable : variables) {
            names.add(safeName(variable));
        }
        return String.join(", ", names);
    }

    private static String frameName(IStackFrame frame) {
        if (frame instanceof IJavaStackFrame javaFrame) {
            try {
                return javaFrame.getMethodName();
            } catch (DebugException e) {
                // fall back to the generic frame name
            }
        }
        try {
            return frame.getName();
        } catch (DebugException e) {
            return "<unknown frame>";
        }
    }

    private static String declaringType(IStackFrame frame) {
        if (frame instanceof IJavaStackFrame javaFrame) {
            try {
                return javaFrame.getDeclaringTypeName();
            } catch (DebugException e) {
                return "";
            }
        }
        return "";
    }

    private static int line(IStackFrame frame) {
        try {
            return frame.getLineNumber();
        } catch (DebugException e) {
            return -1;
        }
    }

    /** true when the frame is a constructor (method entry). */
    private static boolean isMethodEntry(IStackFrame frame) {
        if (frame instanceof IJavaStackFrame javaFrame) {
            try {
                return javaFrame.isConstructor();
            } catch (DebugException e) {
                return false;
            }
        }
        return false;
    }

    private static boolean isSuspended(IJavaThread thread) {
        return thread.isSuspended();
    }

    private static boolean isSuspended(IJavaDebugTarget target) {
        return target.isSuspended();
    }

    private static boolean outOfSynch(IJavaDebugTarget target) {
        try {
            return target.isOutOfSynch();
        } catch (DebugException e) {
            return false;
        }
    }
}
