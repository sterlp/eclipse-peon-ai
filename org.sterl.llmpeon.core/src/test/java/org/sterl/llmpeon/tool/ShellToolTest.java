package org.sterl.llmpeon.tool;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.tool.tools.ShellTool;

class ShellToolTest {

    @TempDir
    Path tempDir;

    ShellTool tool;

    @BeforeEach
    void setUp() {
        tool = new ShellTool();
    }

    @Test
    void runOsCommand_mvnVersion() {
        String result = tool.runOsCommand("mvn -version", tempDir.toString(), null, null);
        assertTrue(result.contains("Apache Maven"), "Expected maven version output, got: " + result);
        
    }

    @Test
    void runOsCommand_emptyCommand_throws() {
        assertThrows(IllegalArgumentException.class, () -> tool.runOsCommand("", tempDir.toString(), null, null));
        assertThrows(IllegalArgumentException.class, () -> tool.runOsCommand(null, tempDir.toString(), null, null));
    }

    @Test
    void runOsCommand_emptyWorkingDir_throws() {
        assertThrows(IllegalArgumentException.class, () -> tool.runOsCommand("echo hi", "", null, null));
        assertThrows(IllegalArgumentException.class, () -> tool.runOsCommand("echo hi", null, null, null));
    }

    @Test
    void runOsCommand_invalidWorkingDir_throws() {
        assertThrows(IllegalArgumentException.class, () -> tool.runOsCommand("echo hi", "/no/such/dir", null, null));
    }

    @Test
    void runOsCommand_tailLinesDefault() {
        // generate more than 50 lines of output
        String os = System.getProperty("os.name").toLowerCase();
        String command = os.contains("win")
                ? "for /L %i in (1,1,100) do @echo line %i"
                : "for i in $(seq 1 100); do echo line $i; done";

        String result = tool.runOsCommand(command, tempDir.toString(), null, null);
        assertTrue(result.contains("lines skipped"), "Should have skipped lines, got: " + result);
        // last line should be present
        assertTrue(result.contains("line 100"));
    }

    @Test
    void runOsCommand_tailLinesAll() {
        String os = System.getProperty("os.name").toLowerCase();
        String command = os.contains("win")
                ? "for /L %i in (1,1,10) do @echo line %i"
                : "for i in $(seq 1 10); do echo line $i; done";

        String result = tool.runOsCommand(command, tempDir.toString(), null, -1);
        assertFalse(result.contains("lines skipped"));
        assertTrue(result.contains("line 1"));
        assertTrue(result.contains("line 10"));
    }

    @Test
    void runOsCommand_nonZeroExitCode() {
        String os = System.getProperty("os.name").toLowerCase();
        String command = os.contains("win") ? "cmd /c exit 42" : "exit 42";

        String result = tool.runOsCommand(command, tempDir.toString(), null, null);
        assertTrue(result.contains("Exit code: 42"), "Expected exit code 42, got: " + result);
    }
}
