package org.sterl.llmpeon.parts.tools.debug;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaPrimitiveValue;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
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

    /** get_state shape: { vm: {name, version, state, outOfSynch}, threads: [{name, state, system, topFrame}] }. */
    static String state(DebugSession session) {
        IJavaDebugTarget target = session.target();
        Map<String, Object> vm = new LinkedHashMap<>();
        vm.put("name", session.vmName());
        vm.put("version", session.vmVersion());
        vm.put("state", isSuspended(target) ? "suspended" : "running");
        vm.put("outOfSynch", outOfSynch(target));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("vm", vm);
        root.put("threads", threadNodes(session.threads()));
        return pretty(root);
    }

    /** get_stack_trace shape: [{index, method, type, line, methodEntry}]. */
    static String stackTrace(IJavaThread thread) {
        IStackFrame[] frames;
        try {
            frames = thread.getStackFrames();
        } catch (DebugException e) {
            throw fail("reading stack frames of thread " + threadName(thread), e);
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
     * get_variables shape: [{name, type, value | fields | length+elements}].
     * {@code namePath} drills into nested variables (a.b.c); {@code depth}
     * bounds field nesting (1..5, 0 = 1).
     */
    public static String variables(IJavaStackFrame frame, String namePath, int depth) {
        int d = depth <= 0 ? 1 : Math.min(depth, MAX_DEPTH);
        IJavaVariable[] locals;
        try {
            locals = frame.getLocalVariables();
        } catch (DebugException e) {
            throw fail("reading local variables of frame " + frameName(frame), e);
        }
        if (namePath == null || namePath.isBlank()) {
            var nodes = new ArrayList<Map<String, Object>>();
            for (IJavaVariable variable : locals) {
                nodes.add(variableNode(variable, d));
            }
            return pretty(nodes);
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

    private static List<Map<String, Object>> threadNodes(List<IJavaThread> threads) {
        var nodes = new ArrayList<Map<String, Object>>();
        for (IJavaThread thread : threads) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("name", threadName(thread));
            node.put("state", isSuspended(thread) ? "suspended" : "running");
            node.put("system", isSystem(thread));
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
            throw fail("reading value of variable " + safeName(variable), e);
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
                throw fail("reading element " + i + " of array " + arrayTypeName(array), e);
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
            throw fail("reading value of variable " + safeName(variable), e);
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
            throw fail("reading fields of variable " + safeName(variable), e);
        }
    }

    private static List<IVariable> childrenOf(IValue value) {
        try {
            return new ArrayList<>(List.of(value.getVariables()));
        } catch (DebugException e) {
            throw fail("reading fields of " + valueString(value), e);
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
            throw fail("reading length of array " + arrayTypeName(array), e);
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

    private static String threadName(IJavaThread thread) {
        try {
            return thread.getName();
        } catch (DebugException e) {
            return "<unknown thread>";
        }
    }

    private static boolean isSuspended(IJavaThread thread) {
        return thread.isSuspended();
    }

    private static boolean isSuspended(IJavaDebugTarget target) {
        return target.isSuspended();
    }

    private static boolean isSystem(IJavaThread thread) {
        try {
            return thread.isSystemThread();
        } catch (DebugException e) {
            return false;
        }
    }

    private static boolean outOfSynch(IJavaDebugTarget target) {
        try {
            return target.isOutOfSynch();
        } catch (DebugException e) {
            return false;
        }
    }

    private static IllegalArgumentException fail(String context, DebugException e) {
        return new IllegalArgumentException(context + " failed: " + e.getMessage(), e);
    }
}
