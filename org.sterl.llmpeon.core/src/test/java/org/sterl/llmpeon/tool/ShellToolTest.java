package org.sterl.llmpeon.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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

    /** prints {@code line <from>} .. {@code line <to>} (one per line) */
    private static String seqCommand(int from, int to) {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win")
                ? "for /L %i in (" + from + ",1," + to + ") do @echo line %i"
                : "for i in $(seq " + from + " " + to + "); do echo line $i; done";
    }

    private static List<String> linesOf(String result) {
        return result.lines().toList();
    }

    /** prints the current working directory */
    private static String pwdCommand() {
        return System.getProperty("os.name").toLowerCase().contains("win") ? "cd" : "pwd";
    }

    @Test
    void runOsCommand_mvnVersion() {
        String result = tool.shellRunCommand("mvn -version", tempDir.toString(), null, null, null);
        assertTrue(result.contains("Apache Maven"), "Expected maven version output, got: " + result);
    }

    @Test
    void runOsCommand_emptyCommand_throws() {
        assertThrows(IllegalArgumentException.class, () -> tool.shellRunCommand("", tempDir.toString(), null, null, null));
        assertThrows(IllegalArgumentException.class, () -> tool.shellRunCommand(null, tempDir.toString(), null, null, null));
    }

    @Test
    void runOsCommand_nullWorkingDir_usesCurrentDir() {
        // Null working directory is allowed - defaults to current dir
        String result = tool.shellRunCommand("echo hi", null, null, null, null);
        assertTrue(result.contains("hi"));
    }

    @Test
    void runOsCommand_invalidWorkingDir_throws() {
        assertThrows(IllegalArgumentException.class, () -> tool.shellRunCommand("echo hi", "/no/such/dir/xyz", null, null, null));
    }

    @Test
    void runOsCommand_tailLinesDefaultIs60() {
        // 100 lines, no tailLines -> last 60 (default), first 40 skipped
        String result = tool.shellRunCommand(seqCommand(1, 100), tempDir.toString(), null, null, null);
        assertTrue(result.contains("40 lines skipped"), "default should skip 40 of 100 lines, got: " + result);
        var lines = linesOf(result);
        assertTrue(lines.contains("line 41"), "default 60 should include line 41, got: " + result);
        assertTrue(lines.contains("line 100"));
        assertFalse(lines.contains("line 40"), "default 60 should not include line 40, got: " + result);
    }

    @Test
    void runOsCommand_tailLinesPositive() {
        // 100 lines, tailLines=20 -> last 20
        String result = tool.shellRunCommand(seqCommand(1, 100), tempDir.toString(), null, 20, null);
        assertTrue(result.contains("80 lines skipped"), "expected 80 skipped, got: " + result);
        var lines = linesOf(result);
        assertTrue(lines.contains("line 81"), "tailLines=20 should include line 81, got: " + result);
        assertTrue(lines.contains("line 100"));
        assertFalse(lines.contains("line 80"), "tailLines=20 should not include line 80, got: " + result);
    }

    @Test
    void runOsCommand_tailLinesMinusOneReturnsAll() {
        // 100 lines so the old default-50 cap would drop the first 50
        String result = tool.shellRunCommand(seqCommand(1, 100), tempDir.toString(), null, -1, null);
        var lines = linesOf(result);
        // R3: first line is the cwd= disclosure now — the first output line moved to line 2 (mechanical pin shift)
        assertTrue(lines.get(1).startsWith("line 1"),
                "tailLines=-1 must return ALL lines starting from the first (line 2 after cwd=), got: " + result);
        assertTrue(lines.contains("line 100"));
        assertFalse(result.contains("lines skipped"),
                "tailLines=-1 must not truncate 100 lines, got: " + result);
    }

    @Test
    void runOsCommand_tailLinesAllHardCap() {
        // 4000 lines, tailLines=-1 -> hard cap 3000, first 1000 skipped
        String result = tool.shellRunCommand(seqCommand(1, 4000), tempDir.toString(), null, -1, null);
        assertTrue(result.contains("1000 lines skipped"), "hard cap should skip 1000 of 4000, got: " + result);
        var lines = linesOf(result);
        assertTrue(lines.contains("line 4000"), "hard cap should include the last line, got: " + result);
        assertTrue(lines.contains("line 1001"), "hard cap should start at line 1001, got: " + result);
        assertFalse(lines.contains("line 1000"), "hard cap should not include line 1000, got: " + result);
    }

    @Test
    void runOsCommand_filterMatch() {
        // filter "line 4" (regex) matches line 4 and line 40..49 = 11 of 100 lines
        String result = tool.shellRunCommand(seqCommand(1, 100), tempDir.toString(), null, null, "line 4");
        assertTrue(result.contains("filter: line 4 (regex, showing 11 of 100 lines)"),
                "expected filter disclosure, got: " + result);
        var lines = linesOf(result);
        assertTrue(lines.contains("line 4"));
        assertTrue(lines.contains("line 40"));
        assertTrue(lines.contains("line 49"));
        assertFalse(lines.contains("line 5"), "non-matching line 5 must be excluded, got: " + result);
        assertFalse(lines.contains("line 14"), "line 14 does not contain 'line 4', got: " + result);
    }

    @Test
    void runOsCommand_filterNoMatch() {
        String result = tool.shellRunCommand(seqCommand(1, 100), tempDir.toString(), null, null, "KEINMATCH");
        assertTrue(result.contains("filter: KEINMATCH (regex, showing 0 of 100 lines)"),
                "expected 0-match disclosure (no silent empty), got: " + result);
    }

    @Test
    void runOsCommand_filterInvalidRegexLiteralFallback() {
        // "[ungültig" is not a valid regex -> literal fallback, exact string match
        String result = tool.shellRunCommand("echo '[ungültig'; echo other", tempDir.toString(), null, null, "[ungültig");
        assertTrue(result.contains("filter: [ungültig (literal, showing 1 of 2 lines)"),
                "expected literal fallback disclosure, got: " + result);
        var lines = linesOf(result);
        assertTrue(lines.contains("[ungültig"));
        assertFalse(lines.contains("other"), "non-matching line must be excluded, got: " + result);
    }

    @Test
    void runOsCommand_filterWithTail() {
        // filter "line" matches all 100 lines; tailLines=20 -> last 20 of the filtered
        String result = tool.shellRunCommand(seqCommand(1, 100), tempDir.toString(), null, 20, "line");
        assertTrue(result.contains("filter: line (regex, showing 20 of 100 lines)"),
                "expected filter+tail disclosure, got: " + result);
        assertTrue(result.contains("80 lines skipped"), "expected tail truncation disclosure, got: " + result);
        var lines = linesOf(result);
        assertTrue(lines.contains("line 100"));
        assertTrue(lines.contains("line 81"));
        assertFalse(lines.contains("line 80"), "tail should not include line 80, got: " + result);
    }

    @Test
    void runOsCommand_filterInErrorPath() {
        // non-zero exit; the filter is still applied and disclosed
        String os = System.getProperty("os.name").toLowerCase();
        String command = os.contains("win") ? "echo boom & exit 3" : "echo boom; echo done; exit 3";
        String result = tool.shellRunCommand(command, tempDir.toString(), null, null, "boom");
        assertTrue(result.contains("filter: boom (regex, showing 1 of 2 lines)"),
                "expected filter disclosure in error path, got: " + result);
        assertTrue(result.contains("Exit code: 3"), "expected exit code, got: " + result);
    }

    @Test
    void runOsCommand_nonZeroExitCode() {
        String os = System.getProperty("os.name").toLowerCase();
        String command = os.contains("win") ? "cmd /c exit 42" : "exit 42";

        String result = tool.shellRunCommand(command, tempDir.toString(), null, null, null);
        assertTrue(result.contains("Exit code: 42"), "Expected exit code 42, got: " + result);
    }

    // UC-TD-2
    @Test
    void shellRunCommand_reportsDurationAndTime() {
        String result = tool.shellRunCommand("sleep 2", tempDir.toString(), null, null, null);
        var last = linesOf(result).get(linesOf(result).size() - 1);
        assertTrue(last.matches("\\(\\d+s, \\d{2}:\\d{2}\\)"),
                "last line should be stats suffix, got: " + last);
        assertTrue(last.contains("2s"), "duration should be measured ~2s, got: " + last);
    }

    // UC-TD-2
    @Test
    void shellRunCommand_timeout_reportsMeasuredDurationAndTime() {
        String result = tool.shellRunCommand("sleep 5", tempDir.toString(), 1, null, null);
        assertTrue(result.contains("Command timed out after 1s"),
                "timeout message should carry measured duration, got: " + result);
        var last = linesOf(result).get(linesOf(result).size() - 1);
        assertTrue(last.matches("\\(\\d+s, \\d{2}:\\d{2}\\)"),
                "last line should be stats suffix, got: " + last);
    }

    // UC-TD-2
    @Test
    void shellRunCommand_nonZeroExit_endsWithStats() {
        String result = tool.shellRunCommand("exit 42", tempDir.toString(), null, null, null);
        assertTrue(result.contains("Exit code: 42"), "expected exit code, got: " + result);
        var lines = linesOf(result);
        assertTrue(lines.get(lines.size() - 1).matches("\\(\\d+s, \\d{2}:\\d{2}\\)"),
                "last line should be stats suffix, got: " + result);
    }

    // R3
    @Test
    void r3DefaultSupplier_usesSupplierDirAndDisclosesCwd() throws Exception {
        var supplierDir = Files.createDirectories(tempDir.resolve("supplier-dir")).toAbsolutePath().normalize();
        tool.setDefaultWorkingDir(() -> supplierDir);

        var lines = linesOf(tool.shellRunCommand(pwdCommand(), null, null, null, null));

        assertEquals("cwd=" + supplierDir, lines.get(0),
                "first line must disclose the effective dir: " + lines);
        // canonical path: on macOS the temp dir sits behind a /var/folders -> /private/var/folders
        // symlink, and pwd reports the physical path (known-risk R3, Plan §5 fallback)
        assertEquals(supplierDir.toFile().getCanonicalPath(), lines.get(1),
                "must execute in the supplier dir, not the process CWD: " + lines);
    }

    // R3
    @Test
    void r3SupplierCallTimeEvaluation_followsChangedReference() throws Exception {
        var dirA = Files.createDirectories(tempDir.resolve("dir-a")).toAbsolutePath().normalize();
        var dirB = Files.createDirectories(tempDir.resolve("dir-b")).toAbsolutePath().normalize();
        var ref = new AtomicReference<Path>(dirA);
        tool.setDefaultWorkingDir(ref::get);

        var first = linesOf(tool.shellRunCommand(pwdCommand(), null, null, null, null));
        assertEquals("cwd=" + dirA, first.get(0), "first call must use the supplier's value: " + first);

        ref.set(dirB);
        var second = linesOf(tool.shellRunCommand(pwdCommand(), null, null, null, null));
        assertEquals("cwd=" + dirB, second.get(0),
                "supplier must be re-read at call time (no frozen field): " + second);
        assertEquals(dirB.toFile().getCanonicalPath(), second.get(1),
                "second call must execute in the new dir (canonical: /var/folders symlink, Plan §5 fallback): " + second);
    }

    // R3
    @Test
    void r3SupplierReturnsNull_fallsBackToProcessCwdWithDisclosure() {
        tool.setDefaultWorkingDir(() -> null);

        var lines = linesOf(tool.shellRunCommand("echo hi", null, null, null, null));

        assertEquals("cwd=" + Path.of(".").toAbsolutePath().normalize(), lines.get(0),
                "null supplier result must fall back to the process CWD (disclosed): " + lines);
        assertEquals("hi", lines.get(1), "the command must still run: " + lines);
    }

    // R3
    @Test
    void r3NoSupplier_set_keepsCurrentDirAndDisclosesCwd() {
        // no setDefaultWorkingDir call at all — regression guard: "." default + disclosure
        var lines = linesOf(tool.shellRunCommand("echo hi", null, null, null, null));

        assertEquals("cwd=" + Path.of(".").toAbsolutePath().normalize(), lines.get(0),
                "without a supplier the process CWD must be used and disclosed: " + lines);
        assertEquals("hi", lines.get(1), "the command must still run: " + lines);
    }

    // R3
    @Test
    void r3ExplicitWorkingDir_ignoresSupplier() throws Exception {
        var dirA = Files.createDirectories(tempDir.resolve("dir-a")).toAbsolutePath().normalize();
        var dirB = Files.createDirectories(tempDir.resolve("dir-b")).toAbsolutePath().normalize();
        tool.setDefaultWorkingDir(() -> dirA);

        var lines = linesOf(tool.shellRunCommand(pwdCommand(), dirB.toString(), null, null, null));

        assertEquals("cwd=" + dirB, lines.get(0),
                "an explicit workingDirectory must win over the supplier: " + lines);
        assertEquals(dirB.toFile().getCanonicalPath(), lines.get(1),
                "must execute in the explicit dir (canonical: /var/folders symlink, Plan §5 fallback): " + lines);
    }
}
