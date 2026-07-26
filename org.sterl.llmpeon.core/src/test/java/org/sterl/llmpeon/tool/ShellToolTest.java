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
        String result = tool.shellRunCommand("mvn -version", tempDir.toString(), null, null);
        assertTrue(result.contains("Apache Maven"), "Expected maven version output, got: " + result);
        
    }

    @Test
    void runOsCommand_emptyCommand_throws() {
        assertThrows(IllegalArgumentException.class, () -> tool.shellRunCommand("", tempDir.toString(), null, null));
        assertThrows(IllegalArgumentException.class, () -> tool.shellRunCommand(null, tempDir.toString(), null, null));
    }

    @Test
    void runOsCommand_nullWorkingDir_usesCurrentDir() {
        // Null working directory is allowed - defaults to current dir
        String result = tool.shellRunCommand("echo hi", null, null, null);
        assertTrue(result.contains("hi"));
    }

    @Test
    void runOsCommand_invalidWorkingDir_throws() {
        assertThrows(IllegalArgumentException.class, () -> tool.shellRunCommand("echo hi", "/no/such/dir/xyz", null, null));
    }

    @Test
    void runOsCommand_tailLinesDefault() {
        // generate more than 50 lines of output
        String os = System.getProperty("os.name").toLowerCase();
        String command = os.contains("win")
                ? "for /L %i in (1,1,100) do @echo line %i"
                : "for i in $(seq 1 100); do echo line $i; done";

        String result = tool.shellRunCommand(command, tempDir.toString(), null, null);
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

        String result = tool.shellRunCommand(command, tempDir.toString(), null, -1);
        assertFalse(result.contains("lines skipped"));
        assertTrue(result.contains("line 1"));
        assertTrue(result.contains("line 10"));
    }
    
    //@Test
    void foo() {
        String result = tool.shellRunCommand(
                "cd /Users/sterlp/dev/workset/peon-ai && mvn clean verify 2>&1", 
                null, null, 50);
        
        System.err.println();
        System.err.println("Done");
        System.err.println(result);
    }

    @Test
    void runOsCommand_nonZeroExitCode() {
        String os = System.getProperty("os.name").toLowerCase();
        String command = os.contains("win") ? "cmd /c exit 42" : "exit 42";

        String result = tool.shellRunCommand(command, tempDir.toString(), null, null);
        assertTrue(result.contains("Exit code: 42"), "Expected exit code 42, got: " + result);
    }
}
