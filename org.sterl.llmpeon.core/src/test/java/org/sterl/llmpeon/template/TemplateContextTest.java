package org.sterl.llmpeon.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class TemplateContextTest {

    private final TemplateContext ctx = new TemplateContext(Path.of("/some/work/dir"));

    @Test
    void testCurrentDateAppearsOnce() {
        var result = ctx.process("Today: ${currentDate}");
        assertEquals("Today: " + LocalDate.now(), result);
    }

    @Test
    void testCurrentDateAppearsTwice() {
        var result = ctx.process("${currentDate} and ${currentDate}");
        var today = LocalDate.now().toString();
        assertEquals(today + " and " + today, result);
    }

    @Test
    void testWorkPathSubstituted() {
        var result = ctx.process("Dir: ${workPath}");
        assertTrue(result.contains(Path.of("/some/work/dir").toString()), "Expected workPath in: " + result);
    }

    @Test
    void testUnknownVariableLeftAsIs() {
        assertEquals("${unknown}", ctx.process("${unknown}"));
    }

    @Test
    void testNullTemplateReturnsNull() {
        assertNull(ctx.process(null));
    }
}
