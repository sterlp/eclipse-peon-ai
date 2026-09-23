package org.sterl.llmpeon.parts.tools.debug;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.debug.core.IJavaThread;

/**
 * Package-private helpers shared by the debug tool classes (thread name,
 * system-thread flag, exception context).
 */
final class DebugSupport {

    private DebugSupport() {
    }

    static String threadName(IJavaThread thread) {
        try {
            return thread.getName();
        } catch (DebugException e) {
            return "<unknown thread>";
        }
    }

    static boolean isSystem(IJavaThread thread) {
        try {
            return thread.isSystemThread();
        } catch (DebugException e) {
            return false;
        }
    }

    static IllegalArgumentException fail(String context, Exception e) {
        return new IllegalArgumentException(context + " failed: " + e.getMessage(), e);
    }
}
