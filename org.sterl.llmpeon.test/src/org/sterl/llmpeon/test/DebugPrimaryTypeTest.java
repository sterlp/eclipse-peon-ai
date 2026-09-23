package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Proxy;
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.junit.Test;
import org.sterl.llmpeon.parts.tools.debug.JavaDebugTool;

/**
 * Regression test for the 2026-09-21 E2E F1: debugJavaSetBreakpoint failed with
 * "not a Java compilation unit" because the raw IFile was used without
 * resolving it through the JDT model. primaryTypeName must work on the
 * resolved ICompilationUnit (JavaCore.createCompilationUnitFrom, which is a
 * static factory and therefore covered by the E2E smoke instead) and fail
 * honestly (file + project + reason) when there is no CU or no primary type.
 */
public class DebugPrimaryTypeTest {

    // === stub model: dynamic proxies answering a per-method map (DebugJsonUnitTest pattern) ===

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, Map<String, Object> answers) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> "stub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> answers.getOrDefault(method.getName(), defaultValue(method.getReturnType()));
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        return null;
    }

    // === tests ===

    // UC-JD-8
    @Test
    public void resolvesPrimaryTypeOfLoadedCompilationUnit() {
        // GIVEN: a JDT compilation unit with a primary type
        IType type = stub(IType.class, Map.of("getFullyQualifiedName", "peontest.DebugFix"));
        ICompilationUnit unit = stub(ICompilationUnit.class, Map.of("findPrimaryType", type));

        // WHEN: resolving the primary type for debugJavaSetBreakpoint
        String typeName = JavaDebugTool.primaryTypeName(unit, "test_project", "src/peontest/DebugFix.java");

        // THEN: the fully qualified name of the primary type
        assertEquals("peontest.DebugFix", typeName);
    }

    // UC-JD-8
    @Test
    public void fileWithoutCompilationUnitFailsHonest() {
        // GIVEN: JDT could not load a compilation unit for the file (null — e.g. not a Java source file)
        // WHEN: resolving the primary type
        try {
            JavaDebugTool.primaryTypeName(null, "test_project", "README.md");
            fail("expected an honest error");
        } catch (IllegalArgumentException e) {
            // THEN: the error names the file, the project and the reason
            assertContains(e.getMessage(), "README.md");
            assertContains(e.getMessage(), "test_project");
            assertContains(e.getMessage(), "not a Java source file");
        }
    }

    // UC-JD-8
    @Test
    public void compilationUnitWithoutPrimaryTypeFailsHonest() {
        // GIVEN: a compilation unit that has no primary type
        ICompilationUnit unit = stub(ICompilationUnit.class, Map.of());

        // WHEN: resolving the primary type
        try {
            JavaDebugTool.primaryTypeName(unit, "test_project", "src/peontest/NoPrimary.java");
            fail("expected an honest error");
        } catch (IllegalArgumentException e) {
            // THEN: the error names the file, the project and the reason
            assertContains(e.getMessage(), "NoPrimary.java");
            assertContains(e.getMessage(), "test_project");
            assertContains(e.getMessage(), "has no primary Java type");
        }
    }

    private static void assertContains(String value, String expected) {
        assertTrue("Expected:\n" + value + "\nto contain:\n" + expected, value.contains(expected));
    }
}
