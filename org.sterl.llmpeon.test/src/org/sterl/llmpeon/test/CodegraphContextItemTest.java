package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.junit.Test;
import org.sterl.llmpeon.agent.model.TaggedResult;
import org.sterl.llmpeon.parts.ai.CodegraphConnectionService.Kind;
import org.sterl.llmpeon.parts.ai.CodegraphConnectionService.State;
import org.sterl.llmpeon.parts.ai.CodegraphContextItem;

/**
 * R-CG-7 / R-CG-12 / R-TR-4/5: the {@link CodegraphContextItem} — {@code label()} (the chat
 * signal, non-null for all three states), {@code dedupKey()} (block start line; CONNECTED only)
 * and {@code render()} (CONNECTED = path mapping + instructions; OFF/ERROR = {@code null},
 * R-TR-4 — visibility stays with the icon + tooltip).
 * Pure unit test: a stub {@link IProject} supplies the name, disk path and nested workspace path.
 */
public class CodegraphContextItemTest {

    private static final String DISK = "/tmp/cg-proj";

    // R-CG-12
    @Test
    public void label_isNonNullForAllThreeStates() {
        assertNotNull("CONNECTED", item(state(Kind.CONNECTED, null)).label());
        assertNotNull("OFF", item(state(Kind.OFF, null)).label());
        assertNotNull("ERROR", item(state(Kind.ERROR, null)).label());
    }

    // R-CG-12
    @Test
    public void label_carriesStateWordAndProject() {
        var labels = List.of(
                item(state(Kind.CONNECTED, null)).label(),
                item(state(Kind.OFF, null)).label(),
                item(state(Kind.ERROR, null)).label());
        assertTrue("connected: " + labels.get(0), labels.get(0).contains("connected") && labels.get(0).contains("cg-proj"));
        assertTrue("off: " + labels.get(1), labels.get(1).contains("off") && labels.get(1).contains("cg-proj"));
        assertTrue("failed: " + labels.get(2), labels.get(2).contains("failed") && labels.get(2).contains("cg-proj"));
    }

    // R-CG-7
    @Test
    public void dedupKey_isBlockStartLine_anyChangeNewKey() {
        var a = item(state(Kind.CONNECTED, "INSTR"));
        var b = item(state(Kind.CONNECTED, "INSTR"));
        assertEquals("same state → same key", a.dedupKey(), b.dedupKey());
        assertEquals("key is the exact block start line",
                TaggedResult.PREFIX + a.render().head(), a.dedupKey());

        assertNull("OFF renders nothing → no key", item(state(Kind.OFF, null)).dedupKey());
        assertNotEquals("project change → new key", a.dedupKey(),
                item(new State(Kind.CONNECTED, project("other", DISK), null, "INSTR")).dedupKey());
        assertNotEquals("instructions change → new key", a.dedupKey(),
                item(state(Kind.CONNECTED, "OTHER")).dedupKey());
    }

    // R-CG-7
    @Test
    public void renderConnected_hasPathMappingAndInstructions() {
        var render = item(state(Kind.CONNECTED, "CG-SERVER-DOCS")).render();
        assertNotNull(render);
        var body = render.body();
        assertTrue("disk path: " + body, body.contains(DISK));
        assertTrue("real workspace path (nested project, not the name): " + body,
                body.contains("/peon-ai/cg-proj"));
        assertTrue("mapping names the tool: " + body, body.contains("codegraph_explore"));
        assertTrue("instructions verbatim: " + body, body.contains("CG-SERVER-DOCS"));
    }

    // R-CG-7
    @Test
    public void renderConnected_withoutProjectOrInstructions_fallsBackToOneLine() {
        // GIVEN CONNECTED with no project (disk/ws null) and no server instructions
        var render = new CodegraphContextItem(
                () -> new State(Kind.CONNECTED, null, null, null)).render();

        // THEN the fallback line is present (CONNECTED never renders empty text)
        assertNotNull(render);
        var body = render.body();
        assertTrue("fallback line: " + body,
                body.contains("codegraph_explore returns absolute disk paths; the eclipse*-tools use workspace paths."));
        assertFalse("one line: " + body, body.contains(System.lineSeparator()));
    }

    // R-TR-4
    @Test
    public void render_returnsNull_whenOff() {
        assertNull(item(state(Kind.OFF, null)).render());
    }

    // R-TR-4
    @Test
    public void render_returnsNull_whenError() {
        assertNull(item(new State(Kind.ERROR, project("cg-proj", DISK), "spawn failed", null)).render());
    }

    // --- helpers ---

    private static CodegraphContextItem item(State s) {
        return new CodegraphContextItem(() -> s);
    }

    private static State state(Kind kind, String instructions) {
        return new State(kind, project("cg-proj", DISK), kind == Kind.ERROR ? "boom" : null, instructions);
    }

    /** Minimal IProject stub — name, location and full path are exercised (label, diskPathOf, pathOf).
     *  The full path is deliberately NESTED (≠ the name) so a regression to "/"+name is caught. */
    private static IProject project(String name, String disk) {
        return (IProject) Proxy.newProxyInstance(
                IProject.class.getClassLoader(),
                new Class<?>[] { IProject.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getRawLocation" -> IPath.fromOSString(disk);
                    case "getLocation" -> IPath.fromOSString(disk);
                    case "getFullPath" -> IPath.fromOSString("/peon-ai/" + name);
                    case "toString" -> "IProject:" + name;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }
}
