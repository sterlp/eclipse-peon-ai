package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeTrue;

import org.junit.Test;
import org.sterl.llmpeon.context.EclipseFileContextItem;

/**
 * S3 (docs/context-message-concept.md): the file context item renders header + content for an
 * existing file and {@code null} (nothing to inject, no exception) for a missing file or project.
 */
public class EclipseFileContextItemTest extends AbstractIntegrationTest {

    @Test
    public void render_returnsHeaderAndContent_whenFileExists() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        eclipseWriteFile("docs/test-memory.md", "memory content");

        var item = new EclipseFileContextItem("docs/test-memory.md", project);
        var workspacePath = "/" + project.getName() + "/docs/test-memory.md";

        assertEquals(workspacePath + ":" + System.lineSeparator() + "---" + System.lineSeparator() + "memory content", item.render());
        assertEquals(workspacePath, item.label());
        assertEquals(workspacePath + ":" + System.lineSeparator() + "---" + System.lineSeparator(), item.dedupKey());
    }

    @Test
    public void render_returnsNull_whenFileMissing() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        var item = new EclipseFileContextItem("docs/does-not-exist.md", project);

        assertNull(item.render());
    }

    @Test
    public void render_returnsNull_whenProjectNull() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        var item = new EclipseFileContextItem("docs/any.md", null);

        assertNull(item.render());
        assertNull(item.label());
        assertNull(item.dedupKey());
    }
}
