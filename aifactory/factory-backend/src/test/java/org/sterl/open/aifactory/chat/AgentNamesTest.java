package org.sterl.open.aifactory.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.poagent.AiPoAgent;
import org.sterl.llmpeon.poagent.tools.PoDelegateTool;

/**
 * The two answers of the factory's one author table (R-WP-4): whose the conversation is, and who speaks
 * through a tool. An id or a tool the factory does not know is nobody — it gets no name and is never Jon.
 */
class AgentNamesTest {

    /** The real table of a runtime, with the team names the factory gives its members. */
    private static AgentNames factoryTeam() {
        return ProjectRuntime.authorTable("Da Thinka", "Da Mek", "Da Dok");
    }

    @Test
    void onlyJonIsJon() {
        AgentNames names = factoryTeam();

        assertThat(names.isJon(AiPoAgent.NAME)).as("the conversation belongs to Jon").isTrue();
        assertThat(names.isJon(AiDevAgent.NAME)).as("a member is never the conversation's own author").isFalse();
        assertThat(names.isJon("Peon-Etwas")).as("no invented author").isFalse();
        assertThat(names.isJon(null)).as("an unclaimed line is nobody's").isFalse();
    }

    @Test
    void toolNamesMapToTheMemberThatOwnsThem() {
        AgentNames names = factoryTeam();

        assertThat(names.authorOf(PoDelegateTool.ASK_DEV)).isEqualTo("Da Mek");
        assertThat(names.authorOf(PoDelegateTool.BUILD_WITH_DEV)).isEqualTo("Da Mek");
        assertThat(names.authorOf(PoDelegateTool.TALK_PLAN)).isEqualTo("Da Thinka");
        assertThat(names.authorOf(PoDelegateTool.PLAN_WITH_PLAN_AGENT)).isEqualTo("Da Thinka");
        assertThat(names.authorOf(PoDelegateTool.REVIEW_PLAN_AGENT)).isEqualTo("Da Dok");
        assertThat(names.authorOf("searchAgent"))
                .as("the search sub-loop has no seat in the team and still has an author")
                .isEqualTo("Da Sniffa");
        assertThat(names.authorOf("diskWriteFile"))
                .as("an ordinary tool is not a member speaking — no name, and therefore no line")
                .isNull();
        assertThat(names.authorOf(null)).isNull();
    }

    @Test
    void addingAMemberNeverRewritesTheTableItCameFrom() {
        AgentNames empty = AgentNames.empty();
        AgentNames withMember = empty.withTool(PoDelegateTool.ASK_DEV, "Da Mek");

        assertThat(withMember.authorOf(PoDelegateTool.ASK_DEV)).isEqualTo("Da Mek");
        assertThat(empty.authorOf(PoDelegateTool.ASK_DEV))
                .as("a runtime builds its table by chaining, so a chain step must not mutate its prefix")
                .isNull();
    }
}
