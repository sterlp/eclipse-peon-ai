package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeTrue;

import org.junit.Test;
import org.sterl.llmpeon.context.EclipseFileContextItem;
import org.sterl.llmpeon.shared.FileLines;

/**
 * S3 (docs/context-message-concept.md): the file context item renders header + content for an
 * existing file and {@code null} (nothing to inject, no exception) for a missing file or project.
 */
public class EclipseFileContextItemTest extends AbstractIntegrationTest {

    @Test
    public void render_returnsHeaderAndContent_whenFileExists() {
        // GIVEN
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        eclipseWriteFile("/test_project/docs/test-memory.md", "memory content");
        var workspacePath = "/" + project.getName() + "/docs/test-memory.md";

        // WHEN
        var item = new EclipseFileContextItem("docs/test-memory.md", () -> project);

        // THEN — dedup key is the exact header (path + ":" + lineSeparator + line-number marker)
        assertEquals(workspacePath + ":" + System.lineSeparator() + " content with line numbers:", item.dedupKey());
        assertEquals(workspacePath, item.label());
        // AND — render is the line-numbered content
        assertEquals(FileLines.format("memory content"), item.render());
    }

    @Test
    public void render_returnsNull_whenFileMissing() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        var item = new EclipseFileContextItem("docs/does-not-exist.md", () -> project);

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
