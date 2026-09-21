package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaThreadGroup;
import org.junit.Test;
import org.sterl.llmpeon.parts.tools.debug.DebugJson;
import org.sterl.llmpeon.parts.tools.debug.DebugSession;

/**
 * Regression test for the 2026-09-21 stale-VM diagnosis (issue2 Befund 1/2):
 * thread enumeration must go through {@code getThreads()} — JDI root thread
 * groups can miss threads (the main thread at a breakpoint hangs in no root
 * group), so the getRootThreadGroups() path silently hid the session's
 * threads. The stubbed target reproduces exactly the diagnosed state:
 * getThreads() = main (suspended) + 5 running system threads,
 * getRootThreadGroups() = the 5 system threads only, target.isSuspended() = false.
 */
public class DebugSessionThreadsTest {

    // === stub model: dynamic proxies answering a per-method map (DebugJsonUnitTest pattern) ===

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<?>[] types, Map<String, Object> answers) {
        return (T) Proxy.newProxyInstance(types[0].getClassLoader(), types,
                (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> "stub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> answers.getOrDefault(method.getName(), defaultValue(method.getReturnType()));
                });
    }

    private static <T> T stub(Class<T> type, Map<String, Object> answers) {
        return stub(new Class<?>[] { type }, answers);
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
        return null;
    }

    /** The diagnosed breakpoint frame: peontest.DebugFix.main, line 11. */
    private static IJavaStackFrame mainFrame() {
        return stub(IJavaStackFrame.class, Map.of(
                "getMethodName", "main",
                "getDeclaringTypeName", "peontest.DebugFix",
                "getLineNumber", 11));
    }

    private static IJavaThread thread(String name, boolean suspended, boolean system) {
        var answers = new HashMap<String, Object>();
        answers.put("getName", name);
        answers.put("isSuspended", suspended);
        answers.put("isSystemThread", system);
        if (!system) {
            answers.put("getTopStackFrame", mainFrame());
        }
        return stub(IJavaThread.class, answers);
    }

    /** The five running system threads from the diagnosis. */
    private static IJavaThread[] systemThreads() {
        return new IJavaThread[] {
                thread("Reference Handler", false, true),
                thread("Finalizer", false, true),
                thread("Signal Dispatcher", false, true),
                thread("JVMCI-native CompilerThread0", false, true),
                thread("Notification Thread", false, true) };
    }

    /**
     * The diagnosed target: getThreads() sees main, getRootThreadGroups() does not
     * (the divergent JDI path — a trap for implementations that still enumerate via groups),
     * target-level isSuspended() false (mixed state). The process is a separate object
     * reached via getProcess() (JDIDebugTarget does NOT implement IProcess), its
     * ATTR_PROCESS_ID attribute comes back as the String "78703".
     */
    private static IJavaDebugTarget target(IJavaThread main) {
        return target(main, stub(IProcess.class, Map.of("getAttribute", "78703")), true);
    }

    private static IJavaDebugTarget target(IJavaThread main, IProcess process, boolean hasThreads) {
        var system = systemThreads();
        var all = new IJavaThread[system.length + 1];
        all[0] = main;
        System.arraycopy(system, 0, all, 1, system.length);
        var group = stub(IJavaThreadGroup.class, Map.of("getThreads", (Object) system));
        var answers = new HashMap<String, Object>();
        answers.put("isTerminated", false);
        answers.put("isSuspended", false);
        answers.put("isOutOfSynch", false);
        answers.put("getVMName", "Java HotSpot(TM) 64-Bit Server VM");
        answers.put("getVersion", "21.0.1");
        answers.put("getThreads", (Object) all);
        answers.put("getRootThreadGroups", (Object) new IJavaThreadGroup[] { group });
        answers.put("getProcess", process);
        answers.put("hasThreads", hasThreads);
        return stub(IJavaDebugTarget.class, answers);
    }

    private static ILaunch launch(IJavaDebugTarget target) {
        var config = stub(ILaunchConfiguration.class, Map.of("getName", "DebugFix"));
        return stub(ILaunch.class, Map.of(
                "getDebugTargets", (Object) new IDebugTarget[] { target },
                "getLaunchConfiguration", config));
    }

    // === tests ===

    @Test
    public void stateShowsMainThreadAndSuspendedVm() {
        // GIVEN: the diagnosed state — main suspended at the breakpoint, 5 running system
        // threads, getRootThreadGroups() without main
        DebugSession session = DebugSession.findActive(new ILaunch[] { launch(target(thread("main", true, false))) });

        // WHEN: rendering get_state
        String json = DebugJson.state(session);

        // THEN: main is listed with its top frame (method main, type DebugFix, line 11)
        assertContains(json, "\"name\" : \"main\"");
        assertContains(json, "\"method\" : \"main\"");
        assertContains(json, "\"type\" : \"peontest.DebugFix\"");
        assertContains(json, "\"line\" : 11");

        // AND: vm.state is suspended although target.isSuspended() is false —
        // a non-system thread is suspended — with the mixed state counted honestly
        assertContains(json, "\"state\" : \"suspended\",\n    \"suspendedThreads\" : 1");

        // AND: the session carries the launch name and the process id
        assertContains(json, "\"session\" : \"DebugFix (pid 78703)\"");
    }

    @Test
    public void stateIsRunningWhenNothingIsSuspended() {
        // GIVEN: main running, system threads running, target not suspended
        DebugSession session = DebugSession.findActive(new ILaunch[] { launch(target(thread("main", false, false))) });

        // WHEN: rendering get_state
        String json = DebugJson.state(session);

        // THEN: vm.state is running and zero suspended non-system threads are counted
        assertContains(json, "\"state\" : \"running\"");
        assertContains(json, "\"suspendedThreads\" : 0");
    }

    @Test
    public void defaultThreadResolutionPicksMainThread() throws Exception {
        // GIVEN: the diagnosed state — main is only visible via getThreads()
        DebugSession session = DebugSession.findActive(new ILaunch[] { launch(target(thread("main", true, false))) });

        // WHEN: resolving the default thread (no name given)
        IJavaThread resolved = session.resolveThread(null);

        // THEN: main is picked (first suspended) — with the old getRootThreadGroups()
        // path this throws "no usable thread" because main is missing there
        assertEquals("main", resolved.getName());
    }

    // UC-JD-2
    @Test
    public void selfExitedVmIsNotAnActiveSession() {
        // GIVEN: a zombie target — after a self-exit JDT leaves the target
        // un-terminated (2026-09-21 E2E F7), but the process is terminated
        IProcess deadProcess = stub(IProcess.class, Map.of("isTerminated", true, "getAttribute", "78703"));
        IJavaDebugTarget zombie = target(thread("main", true, false), deadProcess, true);

        // WHEN: resolving the active session
        DebugSession session = DebugSession.findActive(new ILaunch[] { launch(zombie) });

        // THEN: no active session — get_state answers the no-session message and
        // continue does not poll for 30 s
        assertNull(session);
    }

    // UC-JD-2
    @Test
    public void threadlessVmIsNotAnActiveSession() {
        // GIVEN: a zombie target — process alive, target un-terminated, but the VM
        // answers no threads (the diagnosed get_state showed threads=[])
        IProcess aliveProcess = stub(IProcess.class, Map.of("getAttribute", "78703"));
        IJavaDebugTarget zombie = target(thread("main", true, false), aliveProcess, false);

        // WHEN: resolving the active session
        assertNull(DebugSession.findActive(new ILaunch[] { launch(zombie) }));
    }

    private static void assertContains(String value, String expected) {
        assertTrue("Expected:\n" + value + "\nto contain:\n" + expected, value.contains(expected));
    }
}
