package org.sterl.llmpeon.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.junit.Test;
import org.sterl.llmpeon.parts.ai.CodegraphConnectionService.Kind;
import org.sterl.llmpeon.parts.ai.CodegraphConnectionService.State;
import org.sterl.llmpeon.parts.ai.CodegraphContextItem;

/**
 * R-CG-7 / R-CG-12: the {@link CodegraphContextItem} — {@code label()} (the chat signal, non-null
 * for all three states), {@code dedupKey()} (unchanged state dedups, any change re-injects once)
 * and {@code render()} (CONNECTED = path mapping + usage hint + instructions; OFF/ERROR = one
 * short line). Pure unit test: a stub {@link IProject} supplies the name + disk path.
 */
public class CodegraphContextItemTest {

    private static final String DISK = "/tmp/cg-proj";

    // R-CG-12
    @Test
    public void label_isNonNullForAllThreeStates() {
        assertNotNull("CONNECTED", item(state(Kind.CONNECTED), Optional.empty()).label());
        assertNotNull("OFF", item(state(Kind.OFF), Optional.empty()).label());
        assertNotNull("ERROR", item(state(Kind.ERROR), Optional.empty()).label());
    }

    // R-CG-12
    @Test
    public void label_carriesStateWordAndProject() {
        var labels = List.of(
                item(state(Kind.CONNECTED), Optional.empty()).label(),
                item(state(Kind.OFF), Optional.empty()).label(),
                item(state(Kind.ERROR), Optional.empty()).label());
        assertTrue("connected: " + labels.get(0), labels.get(0).contains("connected") && labels.get(0).contains("cg-proj"));
        assertTrue("off: " + labels.get(1), labels.get(1).contains("off") && labels.get(1).contains("cg-proj"));
        assertTrue("failed: " + labels.get(2), labels.get(2).contains("failed") && labels.get(2).contains("cg-proj"));
    }

    // R-CG-7
    @Test
    public void dedupKey_sameStateSameKey_anyChangeNewKey() {
        var a = item(state(Kind.CONNECTED), Optional.of("INSTR"));
        var b = item(state(Kind.CONNECTED), Optional.of("INSTR"));
        assertEquals("same state → same key", a.dedupKey(), b.dedupKey());

        assertNotEquals("kind change → new key", a.dedupKey(),
                item(state(Kind.OFF), Optional.empty()).dedupKey());
        assertNotEquals("project change → new key", a.dedupKey(),
                item(new State(Kind.CONNECTED, project("other", DISK), null), Optional.of("INSTR")).dedupKey());
        assertNotEquals("instructions change → new key", a.dedupKey(),
                item(state(Kind.CONNECTED), Optional.of("OTHER")).dedupKey());
    }

    // R-CG-7
    @Test
    public void renderConnected_hasPathMappingUsageHintAndInstructions() {
        var render = item(state(Kind.CONNECTED), Optional.of("CG-SERVER-DOCS")).render();
        assertNotNull(render);
        assertTrue("disk path: " + render, render.contains(DISK));
        assertTrue("eclipse path mapping: " + render, render.contains("/cg-proj"));
        assertTrue("usage hint: " + render, render.contains("codegraph_explore"));
        assertTrue("instructions verbatim: " + render, render.contains("CG-SERVER-DOCS"));
    }

    // R-CG-7
    @Test
    public void renderOff_isOneShortLine() {
        var render = item(state(Kind.OFF), Optional.empty()).render();
        assertNotNull(render);
        assertFalse("OFF is one line: " + render, render.contains(System.lineSeparator()));
        assertTrue("names the project: " + render, render.contains("cg-proj"));
        assertTrue("tells install + init: " + render, render.contains("codegraph init"));
    }

    // R-CG-7
    @Test
    public void renderError_isOneShortLineWithReason() {
        var render = item(new State(Kind.ERROR, project("cg-proj", DISK), "spawn failed"), Optional.empty()).render();
        assertNotNull(render);
        assertFalse("ERROR is one line: " + render, render.contains(System.lineSeparator()));
        assertTrue("carries the reason: " + render, render.contains("spawn failed"));
    }

    // --- helpers ---

    private static CodegraphContextItem item(State s, Optional<String> instr) {
        return new CodegraphContextItem(() -> s, () -> instr);
    }

    private static State state(Kind kind) {
        return new State(kind, project("cg-proj", DISK), kind == Kind.ERROR ? "boom" : null);
    }

    /** Minimal IProject stub — only name + location are exercised (label, diskPathOf). */
    private static IProject project(String name, String disk) {
        return (IProject) Proxy.newProxyInstance(
                IProject.class.getClassLoader(),
                new Class<?>[] { IProject.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getRawLocation" -> IPath.fromOSString(disk);
                    case "getLocation" -> IPath.fromOSString(disk);
                    case "getFullPath" -> IPath.fromOSString("/" + name);
                    case "toString" -> "IProject:" + name;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }
}
