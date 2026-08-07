package org.sterl.llmpeon.parts.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.sterl.llmpeon.parts.widget.AiAgentStatusModel.Entry;
import org.sterl.llmpeon.parts.widget.AiAgentStatusModel.Row;
import org.sterl.llmpeon.shared.StringUtil;

/**
 * Pure regression for the PO status-widget render model (agenten-status-im-header-mvp-plan.md,
 * ADR-0025). Da Boss is the first row; the 🟢 leaf rule keeps him calm while a slave works. No SWT —
 * runs headless.
 */
public class AiAgentStatusModelTest {

    private static Row row(String uiName, long tokens, boolean working) {
        return new Row(uiName, tokens, working);
    }

    private static String label(String uiName, long tokens) {
        return uiName + " (" + StringUtil.toK(tokens) + ")";
    }

    @Test
    public void empty_team_renders_no_rows() {
        assertTrue(AiAgentStatusModel.build(List.of()).isEmpty());
    }

    @Test
    public void idle_team_shows_da_boss_then_orks_without_glow() {
        // GIVEN Jon just switched in — Da Boss + his two orks, all idle at 0k
        var entries = AiAgentStatusModel.build(List.of(
                row("Da Boss", 12_000, false),
                row("Da Thinka", 0, false),
                row("Da Mek", 0, false)));

        assertEquals(3, entries.size());
        assertEquals(label("Da Boss", 12_000), entries.get(0).text());
        assertEquals(label("Da Thinka", 0), entries.get(1).text());
        assertEquals(label("Da Mek", 0), entries.get(2).text());
        assertTrue("all calm at rest", entries.stream().noneMatch(Entry::working));
    }

    @Test
    public void da_boss_working_alone_glows_as_the_leaf() {
        // GIVEN Jon is thinking himself, no delegation running
        var entries = AiAgentStatusModel.build(List.of(
                row("Da Boss", 12_000, true),
                row("Da Thinka", 0, false),
                row("Da Mek", 0, false)));

        assertTrue("Da Boss glows when he works alone", entries.get(0).working());
        assertFalse(entries.get(1).working());
        assertFalse(entries.get(2).working());
    }

    @Test
    public void delegating_da_boss_stays_calm_while_the_working_slave_glows() {
        // GIVEN Jon delegated to Da Mek — Jon is technically working, but Da Mek carries the ball
        var entries = AiAgentStatusModel.build(List.of(
                row("Da Boss", 12_000, true),
                row("Da Thinka", 8_000, false),
                row("Da Mek", 45_000, true)));

        assertFalse("leaf rule: Da Boss must NOT glow during delegation", entries.get(0).working());
        assertFalse("idle slave stays calm", entries.get(1).working());
        assertTrue("the busy Da Mek glows", entries.get(2).working());
    }

    @Test
    public void team_of_just_da_boss_glows_on_own_work() {
        // GIVEN a headless Jon without wired slaves — team is only himself
        var entries = AiAgentStatusModel.build(List.of(row("Da Boss", 0, true)));

        assertEquals(1, entries.size());
        assertTrue(entries.get(0).working());
    }
}
