package org.sterl.llmpeon.parts.tools.debug;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaThreadGroup;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;

/**
 * Per-call, stateless resolution of the active Java debug session (D4):
 * zero active (non-terminated) {@link IJavaDebugTarget}s yield the honest
 * no-session message, more than one yields an honest error naming all of them.
 * Never auto-starts and never auto-disconnects (R-JD-1).
 */
public final class DebugSession {

    static final String NO_SESSION = "no active debug session — start debugging in the Debug view first";

    private final ILaunch launch;
    private final IJavaDebugTarget target;

    private DebugSession(ILaunch launch, IJavaDebugTarget target) {
        this.launch = launch;
        this.target = target;
    }

    /** Resolves the active session from the launch manager (never auto-starts, R-JD-1). */
    static DebugSession findActive() {
        return findActive(DebugPlugin.getDefault().getLaunchManager().getLaunches());
    }

    /**
     * Pure session lookup over the given launches.
     *
     * @return the single active session, or {@code null} when none is active
     * @throws IllegalArgumentException when more than one session is active (all listed)
     */
    public static DebugSession findActive(ILaunch[] launches) {
        var active = new ArrayList<DebugSession>();
        for (ILaunch candidate : launches) {
            for (var debugTarget : candidate.getDebugTargets()) {
                if (!(debugTarget instanceof IJavaDebugTarget target)) {
                    continue;
                }
                if (!target.isTerminated()) {
                    active.add(new DebugSession(candidate, target));
                }
            }
        }
        if (active.isEmpty()) {
            return null;
        }
        if (active.size() > 1) {
            var list = new ArrayList<String>();
            for (var session : active) {
                list.add(session.launchName() + " — " + session.vmName());
            }
            throw new IllegalArgumentException("multiple active debug sessions: " + String.join("; ", list)
                    + " — only a single session is supported, stop all but one first");
        }
        return active.get(0);
    }

    IJavaDebugTarget target() {
        return target;
    }

    /** Project of the session launch — base for project-relative file paths. */
    String sessionProject() {
        ILaunchConfiguration config = launch.getLaunchConfiguration();
        if (config == null) {
            return "";
        }
        try {
            return config.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, "");
        } catch (CoreException e) {
            return "";
        }
    }

    String launchName() {
        ILaunchConfiguration config = launch.getLaunchConfiguration();
        return config == null ? "<unknown launch>" : config.getName();
    }

    public String vmName() {
        try {
            return target.getVMName();
        } catch (DebugException e) {
            return "<unknown VM>";
        }
    }

    String vmVersion() {
        try {
            return target.getVersion();
        } catch (DebugException e) {
            return "";
        }
    }

    /**
     * Resolves a thread by name; blank name = default thread (first suspended,
     * else first non-system thread).
     *
     * @throws IllegalArgumentException when the named thread does not exist or no usable thread exists
     */
    /** All root threads of the session target. */
    List<IJavaThread> threads() {
        var threads = new ArrayList<IJavaThread>();
        try {
            for (IJavaThreadGroup group : target.getRootThreadGroups()) {
                for (IJavaThread thread : group.getThreads()) {
                    threads.add(thread);
                }
            }
        } catch (DebugException e) {
            throw DebugSupport.fail("reading threads of " + vmName(), e);
        }
        return threads;
    }

    IJavaThread resolveThread(String name) {
        var threads = threads();
        if (name != null && !name.isBlank()) {
            for (IJavaThread thread : threads) {
                if (name.equals(DebugSupport.threadName(thread))) {
                    return thread;
                }
            }
            throw new IllegalArgumentException("thread '" + name + "' not found — active threads: "
                    + threadNames(threads));
        }
        for (IJavaThread thread : threads) {
            if (thread.isSuspended()) {
                return thread;
            }
        }
        for (IJavaThread thread : threads) {
            if (!DebugSupport.isSystem(thread)) {
                return thread;
            }
        }
        throw new IllegalArgumentException("no usable thread — none suspended and all are system threads");
    }

    /**
     * Resolves the frame at {@code frameIndex} (0 = top) of the thread's stack.
     *
     * @throws IllegalArgumentException when the index is out of range
     */
    IStackFrame resolveFrame(IJavaThread thread, int frameIndex) {
        IStackFrame[] frames;
        try {
            frames = thread.getStackFrames();
        } catch (DebugException e) {
            throw DebugSupport.fail("reading stack frames of thread " + DebugSupport.threadName(thread), e);
        }
        if (frameIndex < 0 || frameIndex >= frames.length) {
            throw new IllegalArgumentException("frame index " + frameIndex + " out of range (0.."
                    + Math.max(frames.length - 1, 0) + ", 0 = top) in thread " + DebugSupport.threadName(thread));
        }
        return frames[frameIndex];
    }

    private static String threadNames(List<IJavaThread> threads) {
        var names = new ArrayList<String>();
        for (IJavaThread thread : threads) {
            names.add(DebugSupport.threadName(thread));
        }
        return String.join(", ", names);
    }
}
