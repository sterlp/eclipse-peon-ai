package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.junit.Test;
import org.sterl.llmpeon.parts.tools.debug.DebugSession;

/**
 * Unit tests for the pure session lookup {@link DebugSession#findActive(ILaunch[])}
 * (R-JD-1 core: terminated targets are filtered out) against hand-stubbed
 * launch/target proxies. No live debug session, no DebugPlugin singleton.
 */
public class DebugSessionLookupTest {

    // === stub model: dynamic proxies answering a per-method map (DebugJsonUnitTest pattern) ===

    private static <T> T stub(Class<T> type, Map<String, Object> answers) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> "stub(" + type.getSimpleName() + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> answers.getOrDefault(method.getName(), defaultValue(method.getReturnType()));
                }));
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        return null;
    }

    private static IJavaDebugTarget javaTarget(boolean terminated, String vmName) {
        var answers = new HashMap<String, Object>();
        answers.put("isTerminated", terminated);
        answers.put("getVMName", vmName);
        return stub(IJavaDebugTarget.class, answers);
    }

    private static ILaunch launch(IDebugTarget... targets) {
        return stub(ILaunch.class, Map.of("getDebugTargets", (Object) targets));
    }

    // === tests ===

    @Test
    public void terminatedTargetIsFilteredOut() {
        // GIVEN: one terminated and one active target
        IJavaDebugTarget terminated = javaTarget(true, "terminated-vm");
        IJavaDebugTarget active = javaTarget(false, "active-vm");

        // WHEN: resolving the active session
        DebugSession session = DebugSession.findActive(new ILaunch[] { launch(terminated), launch(active) });

        // THEN: the terminated target is filtered out, the active one is chosen
        assertEquals("active-vm", session.vmName());
    }

    @Test
    public void activeTargetIsChosen() {
        // GIVEN: a single active target
        IJavaDebugTarget active = javaTarget(false, "active-vm");

        // WHEN: resolving the active session
        DebugSession session = DebugSession.findActive(new ILaunch[] { launch(active) });

        // THEN: it is chosen
        assertEquals("active-vm", session.vmName());
    }

    @Test
    public void allTerminatedYieldsNoSession() {
        // GIVEN: two terminated targets
        IJavaDebugTarget a = javaTarget(true, "vm-a");
        IJavaDebugTarget b = javaTarget(true, "vm-b");

        // WHEN: resolving the active session
        DebugSession session = DebugSession.findActive(new ILaunch[] { launch(a), launch(b) });

        // THEN: no session is returned (R-JD-1: no auto-start, honest no-session)
        assertNull(session);
    }
}
