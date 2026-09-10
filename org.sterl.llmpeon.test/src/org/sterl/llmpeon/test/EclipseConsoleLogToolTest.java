package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.junit.After;
import org.junit.Test;
import org.sterl.llmpeon.parts.tools.EclipseConsoleLogTool;

public class EclipseConsoleLogToolTest extends AbstractUnitTest {

    private PeonTestConsole console;

    @Override
    @After
    public void after() {
        if (console != null) {
            ConsolePlugin.getDefault().getConsoleManager().removeConsoles(new IConsole[] { console });
        }
        super.after();
    }

    @Test
    public void grepFiltersLines() {
        String name = registerConsole(contentWithErrors());

        String result = new EclipseConsoleLogTool().eclipseReadConsoleLog(name, null, "ERROR");
        String body = body(result);

        assertEquals(12, body.lines().count());
        assertTrue(body.contains("ERROR 1"));
        assertTrue(body.contains("ERROR 12"));
        assertFalse(body.contains("INFO"));
    }

    @Test
    public void limitAppliesAfterGrep() {
        String name = registerConsole(contentWithErrors());

        String result = new EclipseConsoleLogTool().eclipseReadConsoleLog(name, 5, "ERROR");
        String body = body(result);

        assertEquals(5, body.lines().count());
        assertTrue(body.contains("ERROR 12"));
        assertFalse(body.contains("ERROR 7\n"));
    }

    @Test
    public void reportsCropCounts() {
        String name = registerConsole(contentWithErrors());

        String result = new EclipseConsoleLogTool().eclipseReadConsoleLog(name, 5, "ERROR");

        assertTrue(result.startsWith("showing 5 of 12 matching lines (console: " + name
                + ", total 5000) · regex search\n"));
    }

    @Test
    public void withoutGrepBehavesAsTail() {
        String name = registerConsole(IntStream.rangeClosed(1, 5000)
                .mapToObj(i -> "line " + i)
                .collect(Collectors.joining("\n")));

        String result = new EclipseConsoleLogTool().eclipseReadConsoleLog(name, null, null);
        String body = body(result);

        assertTrue(result.startsWith("showing 50 of 5000 lines (console: " + name + ")\n"));
        assertEquals(50, body.lines().count());
        assertEquals("line 4951", body.lines().findFirst().orElse(null));
        assertEquals("line 5000", body.lines().reduce((first, second) -> second).orElse(null));
    }

    private String registerConsole(String content) {
        String name = "peon-test-console-" + System.nanoTime();
        console = new PeonTestConsole(name);
        console.setContent(content);
        ConsolePlugin.getDefault().getConsoleManager().addConsoles(new IConsole[] { console });
        return name;
    }

    private String contentWithErrors() {
        return IntStream.rangeClosed(1, 5000)
                .mapToObj(i -> i <= 12 ? "ERROR " + i : "INFO " + i)
                .collect(Collectors.joining("\n"));
    }

    private String body(String result) {
        return result.substring(result.indexOf('\n') + 1);
    }
}
