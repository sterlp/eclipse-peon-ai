package org.sterl.llmpeon.skill;

/**
 * Where a skill was loaded from — displayed to user and LLM so an override
 * situation stays visible (project skills win on name collision).
 */
public enum SkillSource {

    CONFIG(" [config]"),
    PROJECT(" [project]");

    private final String tag;

    SkillSource(String tag) {
        this.tag = tag;
    }

    /** Display suffix, e.g. {@code "review [project]"}. */
    public String tag() {
        return tag;
    }
}
