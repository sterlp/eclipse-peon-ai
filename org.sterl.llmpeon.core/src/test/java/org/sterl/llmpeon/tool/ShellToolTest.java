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

    // BDD-2 e2e guard (docs/output-trim-service.md): 100 short lines, default maxChars -> whole, no note
    @Test
    void runOsCommand_defaultMaxCharsKeepsSmallOutputWhole() {
        String result = tool.shellRunCommand(seqCommand(1, 100), tempDir.toString(), null, null, null);
        var lines = linesOf(result);
        assertTrue(lines.get(1).startsWith("line 1"), "small output must keep the first line (line 2 after cwd=), got: " + result);
        assertTrue(lines.contains("line 100"), "small output must keep the last line, got: " + result);
        assertFalse(result.contains("truncated:"), "100 short lines (< 10k) must not be trimmed, got: " + result);
    }

    // BDD-3 (docs/output-trim-service.md): maxChars=500, ~2400-char output -> head + tail excerpt + disclosure
    @Test
    void runOsCommand_maxCharsHeadTailExcerpt() {
        String os = System.getProperty("os.name").toLowerCase();
        String command = os.contains("win")
                ? "for /L %i in (1,1,100) do @echo line %i padding-padding"
                : "for i in $(seq 1 100); do echo line $i padding-padding; done";
        String result = tool.shellRunCommand(command, tempDir.toString(), null, 500, null);
        var lines = linesOf(result);
        assertTrue(lines.get(1).startsWith("line 1"), "head must keep the first line, got: " + result);
        assertTrue(result.contains("line 100 padding"), "tail must keep the last line, got: " + result);
        assertFalse(result.contains("line 50 padding"), "middle line 50 must be dropped, got: " + result);
        assertTrue(result.contains("truncated:"), "expected trim disclosure, got: " + result);
        assertTrue(result.contains("chars total"), "expected total chars in the disclosure, got: " + result);
        assertTrue(result.contains(" shown"), "expected shown chars in the disclosure, got: " + result);
    }

    // (Paul) no hard cap (docs/output-trim-service.md): maxChars=100000 >> output -> whole, no note
    @Test
    void runOsCommand_maxCharsLargerThanOutputNoTrim() {
        String result = tool.shellRunCommand(seqCommand(1, 100), tempDir.toString(), null, 100000, null);
        var lines = linesOf(result);
        assertTrue(lines.get(1).startsWith("line 1"), "maxChars=100000 must keep the first line (no hard cap), got: " + result);
        assertTrue(lines.contains("line 100"), "all lines must be present, got: " + result);
        assertFalse(result.contains("truncated:"), "output below maxChars must not be trimmed, got: " + result);
    }

    // Default total cap: 4000 lines (~28k chars) -> head + tail near the 10k default, middle dropped
    @Test
    void runOsCommand_defaultTotalCapTrimsLargeOutput() {
        String result = tool.shellRunCommand(seqCommand(1, 4000), tempDir.toString(), null, null, null);
        var lines = linesOf(result);
        assertTrue(lines.get(1).startsWith("line 1"), "head must keep line 1, got: " + result);
        assertTrue(lines.contains("line 4000"), "tail must keep line 4000, got: " + result);
        assertFalse(lines.contains("line 2000"), "middle line 2000 must be dropped, got: " + result);
        assertTrue(result.contains("truncated:"), "expected trim disclosure, got: " + result);
        long shownLineCount = lines.stream().filter(l -> l.startsWith("line ")).count();
        assertTrue(shownLineCount < 1500, "only head+tail should be shown (~1000 lines), got " + shownLineCount);
    }


    // BDD-1 (docs/output-trim-service.md): a 3M-char minified line -> bounded to ~10k + per-line cap marker
    @Test
    void runOsCommand_minifiedLineCapped() {
        String os = System.getProperty("os.name").toLowerCase();
        String command = os.contains("win")
                ? "powershell -NoProfile -Command \"[Console]::Out.Write(('a' * 3000000))\""
                : "head -c 3000000 /dev/zero | tr '\\0' a";
        String result = tool.shellRunCommand(command, tempDir.toString(), null, null, null);
        assertTrue(result.length() <= 10_100, "3M-char line must be bounded to ~10k, got " + result.length() + " chars");
        assertTrue(result.contains("…(+2999500 chars)"), "expected per-line cap marker with cut count, got: " + result);
        assertTrue(result.contains("a".repeat(500)), "first 500 chars must be kept, got: " + result.substring(0, Math.min(120, result.length())));
        var m = java.util.regex.Pattern.compile("truncated: (\\d+) chars total").matcher(result);
        assertTrue(m.find(), "expected 'truncated: N chars total', got: " + result);
        assertTrue(Long.parseLong(m.group(1)) >= 3_000_000, "total must be >= 3M, got " + m.group(1));
    }

    // R4 (docs/shell-tool.md): timeout -> tree-kill, measured "after 1s", partial output uses the
    // same trim. 15k chars WITHOUT a trailing newline = the R4 regression case: the tree-kill's
    // EOF makes readLine deliver the unterminated final line (before the fix a surviving writer
    // held the pipe end open and the partial output was empty).
    @Test
    void runOsCommand_timeout_partialOutputUsesSameTrim() {
        String os = System.getProperty("os.name").toLowerCase();
        String command = os.contains("win")
                ? "powershell -NoProfile -Command \"[Console]::Out.Write(('a' * 15000)); Start-Sleep 5\""
                : "{ head -c 15000 /dev/zero | tr '\\0' a; sleep 5; }";
        String result = tool.shellRunCommand(command, tempDir.toString(), 1, null, null);
        assertTrue(result.contains("Command timed out after 1s"), "expected timeout, got: " + result);
        assertTrue(result.contains("Partial output"), "expected partial output, got: " + result);
        assertTrue(result.contains("truncated:"), "partial output must use the same trim, got: " + result);
        assertTrue(result.length() <= 10_100, "partial output must be bounded to ~10k, got " + result.length());
    }

    // (Task) filter + maxChars: the filter runs before the trim, so all matched lines survive a small budget
    @Test
    void runOsCommand_filterRunsBeforeTrim() {
        String os = System.getProperty("os.name").toLowerCase();
        String command = os.contains("win")
                ? "for /L %i in (1,1,100) do @echo noise %i & for /L %i in (1,1,20) do @echo ERROR %i"
                : "for i in $(seq 1 100); do echo noise $i; done; for i in $(seq 1 20); do echo ERROR $i; done";
        String result = tool.shellRunCommand(command, tempDir.toString(), null, 300, "ERROR");
        assertTrue(result.contains("filter: ERROR (regex, showing 20 of 120 lines)"),
                "expected filter disclosure (20 of 120), got: " + result);
        var lines = linesOf(result);
        for (int i = 1; i <= 20; i++) {
            assertTrue(lines.contains("ERROR " + i), "ERROR line " + i + " must be present (filter before trim), got: " + result);
        }
        assertFalse(result.contains("truncated:"), "filtered 200 chars < 300 maxChars -> no trim, got: " + result);
        assertFalse(result.contains("noise "), "non-matching noise lines must be excluded, got: " + result);
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

    // (Task) filter + maxChars both active: filter disclosure AND trim note in the same output
    @Test
    void runOsCommand_filterWithMaxChars() {
        // filter "line" matches all 100 lines; maxChars=20 -> head line 1 + tail line 100 only
        String result = tool.shellRunCommand(seqCommand(1, 100), tempDir.toString(), null, 20, "line");
        assertTrue(result.contains("filter: line (regex, showing 2 of 100 lines)"),
                "expected filter disclosure (2 of 100), got: " + result);
        assertTrue(result.contains("truncated:"), "expected trim disclosure, got: " + result);
        var lines = linesOf(result);
        assertTrue(lines.contains("line 1"), "head must keep line 1, got: " + result);
        assertTrue(lines.contains("line 100"), "tail must keep line 100, got: " + result);
        assertFalse(lines.contains("line 50"), "middle line 50 must be dropped, got: " + result);
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
