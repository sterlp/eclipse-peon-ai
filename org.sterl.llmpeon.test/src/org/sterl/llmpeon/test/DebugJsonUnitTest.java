package org.sterl.llmpeon.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaPrimitiveValue;
import org.eclipse.jdt.debug.core.IJavaFieldVariable;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaReferenceType;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.junit.Test;
import org.sterl.llmpeon.parts.tools.debug.DebugJson;

/**
 * Unit tests for the DebugJson walker (D5) against hand-stubbed JDT debug model
 * objects — array element limit, field-nesting depth limit, name-path
 * drill-down and pretty output. No live debug session required.
 */
public class DebugJsonUnitTest {

    // === stub model: dynamic proxies answering a per-method map ===

    private static <T> T stub(Class<T> type, Map<String, Object> answers) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> "stub(" + type.getSimpleName() + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> {
                        Object answer = answers.get(method.getName());
                        if (answer instanceof Function<?, ?> fn) {
                            @SuppressWarnings("unchecked")
                            var cast = (Function<Object, Object>) fn;
                            yield cast.apply(args);
                        }
                        if (answer != null) {
                            yield answer;
                        }
                        yield defaultValue(method.getReturnType());
                    }
                }));
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }

    private static IJavaType javaType(String name) {
        return stub(IJavaType.class, Map.of("getName", name));
    }

    private static IJavaValue primitive(String type, Object typedValue, String valueString) {
        var answers = new HashMap<String, Object>();
        answers.put("getJavaType", javaType(type));
        answers.put("getReferenceTypeName", type);
        answers.put("getValueString", valueString);
        answers.put("isNull", false);
        answers.put("isAllocated", true);
        answers.put(switch (type) {
            case "int" -> "getIntValue";
            case "long" -> "getLongValue";
            case "boolean" -> "getBooleanValue";
            case "double" -> "getDoubleValue";
            default -> throw new IllegalArgumentException("test stub: unsupported primitive " + type);
        }, typedValue);
        return stub(IJavaPrimitiveValue.class, answers);
    }

    private static IJavaValue object(String type, String valueString, IVariable... fields) {
        var answers = new HashMap<String, Object>();
        answers.put("getReferenceTypeName", type);
        answers.put("getValueString", valueString);
        answers.put("isNull", false);
        answers.put("isAllocated", true);
        if (fields.length > 0) {
            answers.put("hasVariables", true);
            answers.put("getVariables", (Object) fields);
        }
        return stub(IJavaValue.class, answers);
    }

    private static IJavaValue nullValue(String type) {
        var answers = new HashMap<String, Object>();
        answers.put("getReferenceTypeName", type);
        answers.put("isNull", true);
        return stub(IJavaValue.class, answers);
    }

    private static IJavaVariable variable(String name, String type, IValue value) {
        return stub(IJavaVariable.class,
                Map.of("getName", name, "getReferenceTypeName", type, "getValue", value));
    }

    private static IJavaArray intArray(int length) {
        var answers = new HashMap<String, Object>();
        answers.put("getLength", length);
        answers.put("getReferenceTypeName", "int[]");
        answers.put("getValueString", "int[" + length + "]");
        answers.put("isNull", false);
        answers.put("isAllocated", true);
        answers.put("getValue", (Function<Object, Object>) args -> {
            int index = (Integer) ((Object[]) args)[0];
            return primitive("int", index, String.valueOf(index));
        });
        return stub(IJavaArray.class, answers);
    }

    /** Frame locals: counter=0 (int), p (Point: x=1, y=2, q=Inner: z=7), n (String null), arr (int[25]). No static fields. */
    private static IJavaStackFrame fixtureFrame() {
        return frameWithStatics(new String[0], new IJavaFieldVariable[0]);
    }

    /** The fixture frame with the given declaring-type fields (names and stubs in parallel). */
    private static IJavaStackFrame frameWithStatics(String[] fieldNames, IJavaFieldVariable[] fields) {
        var z = variable("z", "int", primitive("int", 7, "7"));
        var q = variable("q", "peontest.Inner", object("peontest.Inner", "peontest.Inner@2", z));
        var x = variable("x", "int", primitive("int", 1, "1"));
        var y = variable("y", "int", primitive("int", 2, "2"));
        var p = variable("p", "java.awt.Point", object("java.awt.Point", "java.awt.Point@1", x, y, q));
        var counter = variable("counter", "int", primitive("int", 0, "0"));
        var n = variable("n", "java.lang.String", nullValue("java.lang.String"));
        var arr = variable("arr", "int[]", intArray(25));
        var typeAnswers = new HashMap<String, Object>();
        typeAnswers.put("getName", "peontest.DebugFix");
        typeAnswers.put("getAllFieldNames", fieldNames);
        typeAnswers.put("getField", (Function<Object, Object>) args -> {
            String name = (String) ((Object[]) args)[0];
            for (int i = 0; i < fieldNames.length; i++) {
                if (fieldNames[i].equals(name)) {
                    return fields[i];
                }
            }
            return null;
        });
        var type = stub(IJavaReferenceType.class, typeAnswers);
        return stub(IJavaStackFrame.class, Map.of(
                "getLocalVariables", (Object) new IJavaVariable[] { counter, p, n, arr },
                "getReferenceType", type,
                "getMethodName", "main",
                "getDeclaringTypeName", "peontest.DebugFix",
                "getLineNumber", 8,
                "getName", "main() in peontest.DebugFix",
                "isConstructor", false));
    }

    private static IJavaFieldVariable field(String name, String type, IValue value, boolean isStatic) {
        return stub(IJavaFieldVariable.class,
                Map.of("getName", name, "getReferenceTypeName", type, "getValue", value, "isStatic", isStatic));
    }

    /** A non-null throwable object whose getMessage() returns "boom". */
    private static IJavaObject throwableObject(String type) {
        var answers = new HashMap<String, Object>();
        answers.put("getReferenceTypeName", type);
        answers.put("getValueString", type + "@7");
        answers.put("isNull", false);
        answers.put("isAllocated", true);
        answers.put("getJavaType", javaType(type));
        answers.put("sendMessage", (Function<Object, Object>) args -> object("java.lang.String", "boom"));
        return stub(IJavaObject.class, answers);
    }

    private static IJavaThread threadWithTopFrame(IJavaStackFrame frame) {
        return stub(IJavaThread.class, Map.of("getTopStackFrame", frame, "getName", "main"));
    }

    // === tests ===

    @Test
    public void arrayElementLimitIsHonest() {
        // WHEN: reading the frame variables at depth 1
        String json = DebugJson.variables(fixtureFrame(), "", 1);

        // THEN: the array reports its length, the first 20 elements and names the limit
        assertContains(json, "\"name\" : \"arr\"");
        assertContains(json, "\"length\" : 25");
        assertContains(json, "19,");
        assertContains(json, "… 5 more (limit 20 elements)");

        // AND: element index 20 is not rendered
        assertFalse("element index 20 must not be rendered:\n" + json, json.contains("20,"));

        // AND: the shape is { locals, statics } — the fixture frame has no static fields
        assertContains(json, "\"locals\" :");
        assertContains(json, "\"statics\" : [ ]");
    }

    @Test
    public void depthLimitsFieldNesting() {
        // WHEN: depth 1
        String depth1 = DebugJson.variables(fixtureFrame(), "", 1);

        // THEN: the object renders as its value string, no fields
        assertContains(depth1, "\"value\" : \"java.awt.Point@1\"");
        assertFalse("depth 1 must not render fields:\n" + depth1, depth1.contains("\"fields\""));

        // WHEN: depth 2
        String depth2 = DebugJson.variables(fixtureFrame(), "", 2);

        // THEN: one level of fields with values, the nested object stays a value string
        assertContains(depth2, "\"name\" : \"x\"");
        assertContains(depth2, "\"value\" : 1");
        assertContains(depth2, "\"value\" : \"peontest.Inner@2\"");
        assertFalse("depth 2 must not expose the second nesting level:\n" + depth2,
                depth2.contains("\"name\" : \"z\""));

        // WHEN: depth 3
        String depth3 = DebugJson.variables(fixtureFrame(), "", 3);

        // THEN: two levels of fields — z=7 is visible
        assertContains(depth3, "\"name\" : \"z\"");
        assertContains(depth3, "\"value\" : 7");

        // AND: a null object renders as JSON null
        assertContains(depth3, "\"name\" : \"n\"");
        assertContains(depth3, "\"value\" : null");
    }

    @Test
    public void namePathDrillsIntoNestedVariables() {
        // WHEN: drilling into p.x
        String json = DebugJson.variables(fixtureFrame(), "p.x", 1);

        // THEN: only the target variable is rendered
        assertContains(json, "\"name\" : \"x\"");
        assertContains(json, "\"value\" : 1");
        assertFalse("sibling variables must not be rendered:\n" + json, json.contains("\"name\" : \"y\""));

        // WHEN: an unknown path segment
        try {
            DebugJson.variables(fixtureFrame(), "p.nope", 1);
            fail("expected an honest not-found error");
        } catch (IllegalArgumentException e) {
            // THEN: the error names the missing segment and the available scope
            assertContains(e.getMessage(), "nope");
            assertContains(e.getMessage(), "not found");
            assertContains(e.getMessage(), "x");
        }
    }

    // UC-JD-10
    @Test
    public void staticsRenderedSeparatelyFromLocals() {
        // GIVEN: a frame whose declaring type has a static field (limit=42) and a non-static one (cache)
        var limit = field("limit", "int", primitive("int", 42, "42"), true);
        var cache = field("cache", "java.util.Map", object("java.util.HashMap", "java.util.HashMap@3"), false);
        IJavaStackFrame frame = frameWithStatics(new String[] { "limit", "cache" },
                new IJavaFieldVariable[] { limit, cache });

        // WHEN: reading the frame variables
        String json = DebugJson.variables(frame, "", 1);

        // THEN: the static field appears in its own block with name, type and value
        assertContains(json, "\"statics\" :");
        assertContains(json, "\"name\" : \"limit\"");
        assertContains(json, "\"type\" : \"int\"");
        assertContains(json, "\"value\" : 42");

        // AND: the locals keep their previous content
        assertContains(json, "\"locals\" :");
        assertContains(json, "\"name\" : \"counter\"");
        assertContains(json, "\"name\" : \"arr\"");

        // AND: the non-static field must not appear
        assertFalse("non-static fields must not be rendered:\n" + json, json.contains("cache"));
    }

    // UC-JD-10
    @Test
    public void staticsEmptyWhenNone() {
        // GIVEN: a frame whose declaring type has no fields at all (fixtureFrame)

        // WHEN: reading the frame variables
        String json = DebugJson.variables(fixtureFrame(), "", 1);

        // THEN: both keys are present and statics is an empty array
        assertContains(json, "\"locals\" :");
        assertContains(json, "\"statics\" : [ ]");
    }

    // UC-JD-11
    @Test
    public void evaluatedRendersObjectFieldsToDepth2() {
        // GIVEN: an evaluated object with fields name (String "a"), n (int 3) and inner (object with field z)
        var z = variable("z", "int", primitive("int", 7, "7"));
        var inner = variable("inner", "peontest.Inner", object("peontest.Inner", "peontest.Inner@2", z));
        var name = variable("name", "java.lang.String", object("java.lang.String", "a"));
        var n = variable("n", "int", primitive("int", 3, "3"));
        IJavaValue value = object("peontest.Point", "peontest.Point@1", name, n, inner);

        // WHEN: rendering the evaluation result
        String json = DebugJson.evaluated(value);

        // THEN: the object renders its fields with values, not as a bare reference
        assertContains(json, "\"type\" : \"peontest.Point\"");
        assertContains(json, "\"fields\"");
        assertContains(json, "\"name\" : \"name\"");
        assertContains(json, "\"value\" : \"a\"");
        assertContains(json, "\"value\" : 3");
        assertFalse("no bare reference id must be rendered:\n" + json, json.contains("(id="));

        // AND: depth 2 limits the nesting — inner shows its value string, not its own fields
        assertContains(json, "\"value\" : \"peontest.Inner@2\"");
        assertFalse("depth 2 must not expose the second nesting level:\n" + json, json.contains("\"name\" : \"z\""));
    }

    // UC-JD-11
    @Test
    public void evaluatedKeepsPrimitiveStringAndNullAsValues() {
        // WHEN: rendering primitives, a String and null results
        String intJson = DebugJson.evaluated(primitive("int", 3, "3"));
        String stringJson = DebugJson.evaluated(object("java.lang.String", "hello"));
        String nullJson = DebugJson.evaluated(nullValue("java.lang.String"));

        // THEN: they come back as plain values (regression boundary for R-JD-9)
        assertContains(intJson, "\"value\" : 3");
        assertContains(stringJson, "\"value\" : \"hello\"");
        assertFalse("a String without fields must not render a fields block:\n" + stringJson,
                stringJson.contains("\"fields\""));
        assertContains(nullJson, "\"type\" : \"null\"");
        assertContains(nullJson, "\"value\" : null");
    }

    // UC-JD-9
    @Test
    public void valueResponseRendersPrimitivesAsJsonPrimitives() {
        // WHEN: a set_variable response for an int variable (E2E F3: "value":"42" → 42)
        String json = DebugJson.valueResponse("counter", "int", primitive("int", 42, "42"));

        // THEN: the value is a JSON number, not a quoted string (consistency with get_variables)
        assertContains(json, "\"value\" : 42");
        assertFalse("primitives must not be quoted:\n" + json, json.contains("\"value\" : \"42\""));

        // AND: a boolean renders as a JSON boolean
        assertContains(DebugJson.valueResponse("flag", "boolean", primitive("boolean", true, "true")),
                "\"value\" : true");

        // AND: a String variable stays a JSON string
        assertContains(DebugJson.valueResponse("n", "java.lang.String", object("java.lang.String", "hello")),
                "\"value\" : \"hello\"");
    }

    @Test
    public void outputIsPrettyPrinted() {
        // WHEN: rendering any shape
        String json = DebugJson.variables(fixtureFrame(), "", 1);

        // THEN: the output is multi-line pretty JSON, not a single line
        assertTrue("expected multi-line pretty JSON:\n" + json, json.contains("\n  "));
    }

    // UC-JD-12
    @Test
    public void exceptionFindsThrowableInTopFrame() {
        // GIVEN: a thread whose top frame holds an int and a caught IllegalStateException
        var count = variable("count", "int", primitive("int", 3, "3"));
        var e = variable("e", "java.lang.IllegalStateException", throwableObject("java.lang.IllegalStateException"));
        IJavaStackFrame frame = stub(IJavaStackFrame.class, Map.of(
                "getLocalVariables", (Object) new IJavaVariable[] { count, e },
                "getMethodName", "main"));
        IJavaThread thread = threadWithTopFrame(frame);

        // WHEN: looking for the exception
        String json = DebugJson.exception(thread);

        // THEN: the throwable is reported with variable name, type and message
        assertContains(json, "\"varName\" : \"e\"");
        assertContains(json, "\"type\" : \"java.lang.IllegalStateException\"");
        assertContains(json, "\"message\" : \"boom\"");

        // AND: non-throwable locals are not reported
        assertFalse("non-throwable variables must not be reported:\n" + json, json.contains("count"));
    }

    // UC-JD-12
    @Test
    public void exceptionHonestErrorWhenNone() {
        // GIVEN: a thread whose top frame holds only a primitive local
        IJavaStackFrame frame = stub(IJavaStackFrame.class, Map.of(
                "getLocalVariables", (Object) new IJavaVariable[] { variable("count", "int", primitive("int", 3, "3")) },
                "getMethodName", "main"));
        IJavaThread thread = threadWithTopFrame(frame);

        // WHEN: looking for the exception
        try {
            DebugJson.exception(thread);
            fail("expected an honest not-found error");
        } catch (IllegalArgumentException e) {
            // THEN: the error says there is no exception variable
            assertContains(e.getMessage(), "no exception variable in top frame");
        }
    }

    // UC-JD-12
    @Test
    public void exceptionNotRecognizedByUnusualName() {
        // GIVEN: a thread whose top frame holds a custom throwable named neither *Exception nor *Error
        var failure = variable("failure", "my.company.WeirdThrowable", throwableObject("my.company.WeirdThrowable"));
        IJavaStackFrame frame = stub(IJavaStackFrame.class, Map.of(
                "getLocalVariables", (Object) new IJavaVariable[] { failure },
                "getMethodName", "main"));
        IJavaThread thread = threadWithTopFrame(frame);

        // WHEN: looking for the exception
        try {
            DebugJson.exception(thread);
            fail("expected an honest not-found error");
        } catch (IllegalArgumentException e) {
            // THEN: the name-heuristic boundary is reported honestly, not a silent false negative
            assertContains(e.getMessage(), "no exception variable in top frame");
        }
    }

    private static void assertContains(String value, String expected) {
        assertTrue("Expected:\n" + value + "\nto contain:\n" + expected, value.contains(expected));
    }
}
