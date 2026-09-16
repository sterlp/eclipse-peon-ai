package org.sterl.llmpeon.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IOrdinaryClassFile;
import org.eclipse.jdt.core.IType;
import org.eclipse.jface.text.ITextSelection;
import org.junit.Test;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.context.UserContext;

/**
 * Unit tests for the UI-update contract of UserContext's setters (both return true only if an
 * UI update is needed, no change -> false) and the R-SEL selection semantics (UC-SEL-1..4).
 */
public class UserContextTest {

    // === setTextSelection ===

    @Test
    public void firstTextSelectionSetNeedsUiUpdate() {
        // GIVEN a fresh context without text selection
        var context = new UserContext();
        var selection = textSelection(10, 20);

        // WHEN the first text selection is set
        var changed = context.setTextSelection(selection);

        // THEN an UI update is needed and the selection is stored
        assertTrue(changed);
        assertSame(selection, context.getTextSelection());
    }

    @Test
    public void sameTextSelectionReferenceAgainNeedsNoUiUpdate() {
        // GIVEN a context with a text selection
        var context = new UserContext();
        var selection = textSelection(10, 20);
        context.setTextSelection(selection);

        // WHEN the very same reference is set again
        var changed = context.setTextSelection(selection);

        // THEN no UI update is needed
        assertFalse(changed);
    }

    @Test
    public void newReferenceWithSameLinesNeedsNoUiUpdate() {
        // GIVEN a context with a text selection on lines 10-20
        var context = new UserContext();
        context.setTextSelection(textSelection(10, 20));

        // WHEN a different reference with the same start/end lines is set
        var changed = context.setTextSelection(textSelection(10, 20));

        // THEN no UI update is needed (only line changes matter)
        assertFalse(changed);
    }

    @Test
    public void endLineChangeNeedsUiUpdate() {
        // GIVEN a context with a text selection on lines 10-20
        var context = new UserContext();
        context.setTextSelection(textSelection(10, 20));

        // WHEN a selection with a different end line is set
        var changed = context.setTextSelection(textSelection(10, 25));

        // THEN an UI update is needed
        assertTrue(changed);
    }

    @Test
    public void startLineChangeNeedsUiUpdate() {
        // GIVEN a context with a text selection on lines 10-20
        var context = new UserContext();
        context.setTextSelection(textSelection(10, 20));

        // WHEN a selection with a different start line is set
        var changed = context.setTextSelection(textSelection(12, 20));

        // THEN an UI update is needed
        assertTrue(changed);
    }

    @Test
    public void clearingTextSelectionNeedsUiUpdate() {
        // GIVEN a context with a text selection
        var context = new UserContext();
        context.setTextSelection(textSelection(10, 20));

        // WHEN the text selection is cleared
        var changed = context.setTextSelection(null);

        // THEN an UI update is needed and the selection is gone
        assertTrue(changed);
        assertNull(context.getTextSelection());
    }

    // === setSelectedResource ===

    @Test
    public void firstResourceSetNeedsUiUpdateAndStoresResource() {
        // GIVEN a fresh context without selected resource
        var context = new UserContext();
        var resource = resource("/p/a.txt");

        // WHEN the first resource is selected
        var changed = context.setSelectedResource(resource);

        // THEN an UI update is needed and the resource is stored
        assertTrue(changed);
        assertSame(resource, context.getSelectedResource());
    }

    @Test
    public void sameResourceAgainNeedsNoUiUpdateAndKeepsTextSelection() {
        // GIVEN a context with a selected resource and a text selection on it
        // (the text selection is set AFTER the resource — a resource change clears it)
        var context = new UserContext();
        context.setSelectedResource(resource("/p/a.txt"));
        var selection = textSelection(10, 20);
        context.setTextSelection(selection);

        // WHEN a resource with the same path is selected again
        var changed = context.setSelectedResource(resource("/p/a.txt"));

        // THEN no UI update is needed and the text selection is kept
        assertFalse(changed);
        assertSame(selection, context.getTextSelection());
    }

    @Test
    public void differentResourceNeedsUiUpdateAndClearsTextSelection() {
        // GIVEN a context with a text selection and a selected resource
        var context = new UserContext();
        context.setTextSelection(textSelection(10, 20));
        context.setSelectedResource(resource("/p/a.txt"));

        // WHEN a different resource is selected
        var changed = context.setSelectedResource(resource("/p/b.txt"));

        // THEN an UI update is needed and the previous text selection is cleared
        assertTrue(changed);
        assertNull(context.getTextSelection());
    }

    @Test
    public void deselectingResourceNeedsUiUpdate() {
        // GIVEN a context with a selected resource
        var context = new UserContext();
        context.setSelectedResource(resource("/p/a.txt"));

        // WHEN the selection is deselected
        var changed = context.setSelectedResource(null);

        // THEN an UI update is needed and no resource is selected anymore
        assertTrue(changed);
        assertNull(context.getSelectedResource());
    }

    @Test
    public void resourceSetAfterJavaTypeNeedsUiUpdateAndStoresResource() {
        // GIVEN a context with a selected java type
        var context = new UserContext();
        context.setJavaType(iType("com.example.Foo"));
        var resource = resource("/p/a.txt");

        // WHEN a resource is selected afterwards
        var changed = context.setSelectedResource(resource);

        // THEN an UI update is needed, the resource is stored and the type is cleared
        assertTrue(changed);
        assertSame(resource, context.getSelectedResource());
        assertNull(context.getJavaType());
    }

    @Test
    public void nullToNullResourceSetNeedsNoUiUpdate() {
        // GIVEN a fresh context without selected resource
        var context = new UserContext();

        // WHEN null is set as resource
        var changed = context.setSelectedResource(null);

        // THEN no UI update is needed
        assertFalse(changed);
        assertNull(context.getSelectedResource());
    }

    // === R-SEL selection semantics (UC-SEL-1..3) ===

    // UC-SEL-1
    @Test
    public void test_rSel1_nonTextSelectionEventKeepsTextSelection() {
        // GIVEN a text selection in A.java (resource first, then the selection)
        var context = new UserContext();
        context.setSelectedResource(resource("/p/A.java"));
        context.setTextSelection(textSelection(9, 9));

        // WHEN a non-text selection event arrives without a resource (e.g. outline click)
        context.setSelectedResource(null);

        // THEN the selection survives and get() contains the selection context
        var item = item(context.get(), "User text selection");
        assertNotNull(item);
        assertTrue(item.render().contains("10: selected text"));
    }

    // UC-SEL-1
    @Test
    public void test_rSel1_differentResourceClearsTextSelection() {
        // Characterization test — green before and after the fix (plan §3.3).
        // GIVEN a text selection in A.java
        var context = new UserContext();
        context.setSelectedResource(resource("/p/A.java"));
        var selection = textSelection(9, 9);
        context.setTextSelection(selection);

        // WHEN a different resource B.java is selected
        var changed = context.setSelectedResource(resource("/p/B.java"));

        // THEN an UI update is needed and the previous text selection is cleared
        assertTrue(changed);
        assertNull(context.getTextSelection());
    }

    // UC-SEL-2
    @Test
    public void test_rSel2_selectionWithoutResourceIsStillSent() {
        // GIVEN a pure text selection without project or resource
        var context = new UserContext();
        context.setTextSelection(textSelection(9, 9));

        // WHEN the context is requested
        var item = item(context.get(), "User text selection");

        // THEN the selection goes out with a line-numbered snippet — no silent drop
        assertNotNull(item);
        assertTrue(item.render().contains("10: selected text"));
    }

    // UC-SEL-3
    @Test
    public void test_rSel3_snippetPlusFilePath_notFullContent() {
        // GIVEN a selection in a known IFile whose full content carries a marker
        var context = new UserContext();
        context.setSelectedResource(file("/p/A.java"));
        context.setTextSelection(textSelection(9, 9));

        // WHEN the context is requested
        var item = item(context.get(), "User text selection");

        // THEN the item carries path + selected lines + snippet, but NOT the full file content
        assertNotNull(item);
        var render = item.render();
        assertTrue(render.contains("/p/A.java"));
        assertTrue(render.contains("Selected lines 10-10"));
        assertTrue(render.contains("10: selected text"));
        assertFalse(render.contains("FULL-FILE-MARKER"));
    }

    // === R-SEL-4: type selection replaces file/text selection (UC-SEL-4) ===

    // UC-SEL-4
    @Test
    public void test_ucSel4_typeSelectionReplacesResourceAndText() {
        // GIVEN a file selection with a text selection on it
        var context = new UserContext();
        context.setSelectedResource(resource("/p/A.java"));
        context.setTextSelection(textSelection(9, 9));
        var type = iType("com.example.Foo");

        // WHEN a type selection event arrives (fresh context: no project)
        context.setJavaType(type);

        // THEN the type wins: resource and text selection are cleared, get() reports the type
        assertSame(type, context.getJavaType());
        assertNull(context.getSelectedResource());
        assertNull(context.getTextSelection());
        var renders = rendersOf(context.get());
        assertTrue(renders.contains("Java type selected: com.example.Foo"));
    }

    // UC-SEL-4
    @Test
    public void test_ucSel4_classFileSelectionReplacesResourceAndText() {
        // GIVEN a file selection with a text selection on it
        var context = new UserContext();
        context.setSelectedResource(resource("/p/A.java"));
        context.setTextSelection(textSelection(9, 9));
        var classFile = classFile("com.example.Foo");

        // WHEN a class-file selection event arrives (fresh context: no project)
        context.setJavaType(classFile);

        // THEN the class file wins: resource and text selection are cleared, get() reports the type
        assertSame(classFile, context.getJavaType());
        assertNull(context.getSelectedResource());
        assertNull(context.getTextSelection());
        var renders = rendersOf(context.get());
        assertTrue(renders.contains("Java type selected: com.example.Foo"));
    }

    // UC-SEL-4
    @Test
    public void test_ucSel4_textSelectionReplacesType() {
        // GIVEN a type selection
        var context = new UserContext();
        context.setJavaType(iType("com.example.Foo"));

        // WHEN a new text selection arrives
        context.setTextSelection(textSelection(9, 9));

        // THEN the text selection wins and the type is cleared
        assertNull(context.getJavaType());
        var item = item(context.get(), "User text selection");
        assertNotNull(item);
        assertTrue(item.render().contains("10: selected text"));
    }

    // UC-SEL-4
    @Test
    public void test_ucSel4_otherResourceReplacesType() {
        // GIVEN a type selection
        var context = new UserContext();
        context.setJavaType(iType("com.example.Foo"));
        var other = resource("/p/B.java");

        // WHEN a different concrete file is selected
        context.setSelectedResource(other);

        // THEN the file wins and the type is cleared
        assertNull(context.getJavaType());
        assertSame(other, context.getSelectedResource());
        var renders = rendersOf(context.get());
        assertTrue(renders.contains("File selected: /p/B.java"));
    }

    // UC-SEL-4
    @Test
    public void test_ucSel4_typeSurvivesNullEvent() {
        // GIVEN a type selection
        var context = new UserContext();
        var type = iType("com.example.Foo");
        context.setJavaType(type);

        // WHEN a null selection event arrives (no resource)
        context.setSelectedResource(null);

        // THEN the type survives (R-SEL-1 spirit: state stays until something new is selected)
        assertSame(type, context.getJavaType());
    }

    // UC-SEL-4
    @Test
    public void test_ucSel4_caretEventReplacesType() {
        // GIVEN a type selection
        var context = new UserContext();
        context.setJavaType(iType("com.example.Foo"));

        // WHEN an empty caret text event arrives
        context.setTextSelection(new FakeTextSelection(9, 9, ""));

        // THEN the type is cleared (every text event wins, SOLL Q1)
        assertNull(context.getJavaType());
    }

    // === Stubs ===

    private record FakeTextSelection(int startLine, int endLine, String text) implements ITextSelection {
        @Override public int getOffset() { return 0; }
        @Override public int getLength() { return text.length(); }
        @Override public String getText() { return text; }
        @Override public int getStartLine() { return startLine; }
        @Override public int getEndLine() { return endLine; }
        @Override public boolean isEmpty() { return text.isEmpty(); }
        @Override public String toString() { return "textSelection(" + startLine + "-" + endLine + ")"; }
    }

    private static ITextSelection textSelection(int startLine, int endLine) {
        return new FakeTextSelection(startLine, endLine, "selected text");
    }

    /** Minimal IResource stub — only getFullPath() is used by UserContext (via JdtUtil.pathOf). */
    private static IResource resource(String fullPath) {
        return (IResource) Proxy.newProxyInstance(
                UserContextTest.class.getClassLoader(),
                new Class<?>[] { IResource.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getFullPath" -> IPath.fromOSString(fullPath);
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "resource(" + fullPath + ")";
                    default -> method.getReturnType().isPrimitive() ? primitiveDefault(method.getReturnType()) : null;
                });
    }

    /** IFile stub — getFullPath() for JdtUtil.pathOf; readString() returns a recognizable marker. */
    private static IFile file(String fullPath) {
        return (IFile) Proxy.newProxyInstance(
                UserContextTest.class.getClassLoader(),
                new Class<?>[] { IFile.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getFullPath" -> IPath.fromOSString(fullPath);
                    case "readString" -> "FULL-FILE-MARKER";
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "file(" + fullPath + ")";
                    default -> method.getReturnType().isPrimitive() ? primitiveDefault(method.getReturnType()) : null;
                });
    }

    /** IType stub — getFullyQualifiedName() is the name source for UserContext.getName. */
    private static IType iType(String fullyQualifiedName) {
        return (IType) Proxy.newProxyInstance(
                UserContextTest.class.getClassLoader(),
                new Class<?>[] { IType.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getFullyQualifiedName" -> fullyQualifiedName;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "iType(" + fullyQualifiedName + ")";
                    default -> method.getReturnType().isPrimitive() ? primitiveDefault(method.getReturnType()) : null;
                });
    }

    /** IOrdinaryClassFile stub — getType() yields the IType stub (clean name source). */
    private static IOrdinaryClassFile classFile(String fullyQualifiedName) {
        var type = iType(fullyQualifiedName);
        return (IOrdinaryClassFile) Proxy.newProxyInstance(
                UserContextTest.class.getClassLoader(),
                new Class<?>[] { IOrdinaryClassFile.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getType" -> type;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "classFile(" + fullyQualifiedName + ")";
                    default -> method.getReturnType().isPrimitive() ? primitiveDefault(method.getReturnType()) : null;
                });
    }

    private static ContextItem item(List<ContextItem> items, String label) {
        return items.stream().filter(i -> label.equals(i.label())).findFirst().orElse(null);
    }

    /** All rendered texts — the 1-arg SimpleContextItem carries its message in text, not label. */
    private static List<String> rendersOf(List<ContextItem> items) {
        return items.stream().map(ContextItem::render).toList();
    }

    private static Object primitiveDefault(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0d;
        if (type == float.class) return 0.0f;
        return 0;
    }
}
