package org.sterl.open.aifactory.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.agent.AiReviewAgent;
import org.sterl.llmpeon.poagent.AiPoAgent;

/**
 * The factory names only what it built (R-WP-4, D9): the keys are the core's agent ids, and an origin it
 * never created gets no display name and is never the conversation's own author.
 */
class AgentNamesTest {

    private static AgentNames factoryTeam() {
        return AgentNames.ofJon(AiPoAgent.NAME, "Jon")
                .add(AiPlanAgent.NAME, "Da Thinka")
                .add(AiDevAgent.NAME, "Da Mek")
                .add(AiReviewAgent.NAME, "Da Dok");
    }

    @Test
    void unknownOriginHasNoDisplayName() {
        AgentNames names = factoryTeam();

        assertThat(names.display(AiDevAgent.NAME)).isEqualTo("Da Mek");
        assertThat(names.display(AiPoAgent.NAME)).isEqualTo("Jon");
        assertThat(names.display("Peon-Etwas")).as("no invented author").isNull();
        assertThat(names.display(null)).isNull();
        assertThat(names.display("Peon-Search"))
                .as("Da Sniffa stays a tool, not a team member — so he has no name here (D6)")
                .isNull();

        assertThat(names.isJon(AiPoAgent.NAME)).isTrue();
        assertThat(names.isJon(AiDevAgent.NAME)).as("a member is never the conversation's own author").isFalse();
        assertThat(names.isJon("Peon-Etwas")).isFalse();
        assertThat(names.isJon(null)).isFalse();
    }

    @Test
    void addingAMemberNeverRewritesTheTableItCameFrom() {
        AgentNames withJonOnly = AgentNames.ofJon(AiPoAgent.NAME, "Jon");
        AgentNames withMember = withJonOnly.add(AiDevAgent.NAME, "Da Mek");

        assertThat(withMember.display(AiDevAgent.NAME)).isEqualTo("Da Mek");
        assertThat(withJonOnly.display(AiDevAgent.NAME))
                .as("a runtime builds its table by chaining, so a chain step must not mutate its prefix")
                .isNull();
        assertThat(withJonOnly.isJon(AiPoAgent.NAME)).as("and it keeps its own author").isTrue();
    }
}
