package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceReadFileTool;
import org.sterl.llmpeon.tool.tools.DiskFileReadTool;

public class DiskReadFileToolTest extends AbstractIntegrationTest {

    private static final String RELATIVE_PATH = "data/lines-120.txt";
    private static final String WORKSPACE_PATH = "/test_project/" + RELATIVE_PATH;

    @Test
    public void clampsEndLineToFileEnd() {
        var content = diskTool().diskReadFile(RELATIVE_PATH, 100, 900);
        var lines = content.split("\\R");

        assertEquals(21, lines.length);
        assertTrue(lines[0].trim().startsWith("100: "));
        assertTrue(lines[20].trim().startsWith("120: "));
    }

    @Test
    public void matchesEclipseReadFile() {
        var disk = diskTool();
        var eclipse = new EclipseWorkspaceReadFileTool();
        int[][] ranges = { { 100, 900 }, { 800, 0 }, { 0, 0 } };

        for (int[] range : ranges) {
            assertEquals(eclipse.eclipseReadFile(WORKSPACE_PATH, range[0], range[1]),
                    disk.diskReadFile(RELATIVE_PATH, range[0], range[1]));
        }
    }

    private DiskFileReadTool diskTool() {
        return new DiskFileReadTool(PeonTestFixture.dir().toPath());
    }
}
