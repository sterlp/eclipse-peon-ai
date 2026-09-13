package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.agent.AiAgentStatusModel.Entry;
import org.sterl.llmpeon.agent.AiAgentStatusModel.Row;
import org.sterl.llmpeon.shared.StringUtil;

/**
 * Pure regression for the PO status-widget render model (agenten-status-im-header-mvp-plan.md,
 * ADR-0025). Da Boss is the first row; the 🟢 leaf rule keeps him calm while a slave works. No SWT —
 * runs headless.
 */
class AiAgentStatusModelTest {

    private static Row row(String uiName, long tokens, boolean working) {
        return new Row(uiName, tokens, working);
    }

    private static String label(String uiName, long tokens) {
        return uiName + " (" + StringUtil.toK(tokens) + ")";
    }

    @Test
    void empty_team_renders_no_rows() {
        assertThat(AiAgentStatusModel.build(List.of())).isEmpty();
    }

    @Test
    void idle_team_shows_da_boss_then_orks_without_glow() {
        // GIVEN Jon just switched in — Da Boss + his two orks, all idle at 0k
        var entries = AiAgentStatusModel.build(List.of(
                row("Da Boss", 12_000, false),
                row("Da Thinka", 0, false),
                row("Da Mek", 0, false)));

        assertThat(entries).hasSize(3);
        assertThat(entries.get(0).text()).isEqualTo(label("Da Boss", 12_000));
        assertThat(entries.get(1).text()).isEqualTo(label("Da Thinka", 0));
        assertThat(entries.get(2).text()).isEqualTo(label("Da Mek", 0));
        assertThat(entries).as("all calm at rest").noneMatch(Entry::working);
    }

    @Test
    void da_boss_working_alone_glows_as_the_leaf() {
        // GIVEN Jon is thinking himself, no delegation running
        var entries = AiAgentStatusModel.build(List.of(
                row("Da Boss", 12_000, true),
                row("Da Thinka", 0, false),
                row("Da Mek", 0, false)));

        assertThat(entries.get(0).working()).as("Da Boss glows when he works alone").isTrue();
        assertThat(entries.get(1).working()).isFalse();
        assertThat(entries.get(2).working()).isFalse();
    }

    @Test
    void delegating_da_boss_stays_calm_while_the_working_slave_glows() {
        // GIVEN Jon delegated to Da Mek — Jon is technically working, but Da Mek carries the ball
        var entries = AiAgentStatusModel.build(List.of(
                row("Da Boss", 12_000, true),
                row("Da Thinka", 8_000, false),
                row("Da Mek", 45_000, true)));

        assertThat(entries.get(0).working())
                .as("leaf rule: Da Boss must NOT glow during delegation").isFalse();
        assertThat(entries.get(1).working()).as("idle slave stays calm").isFalse();
        assertThat(entries.get(2).working()).as("the busy Da Mek glows").isTrue();
    }

    @Test
    void team_of_just_da_boss_glows_on_own_work() {
        // GIVEN a headless Jon without wired slaves — team is only himself
        var entries = AiAgentStatusModel.build(List.of(row("Da Boss", 0, true)));

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).working()).isTrue();
    }
}
