package org.sterl.llmpeon.prompt;

/**
 * Single source of truth for the workspace paths Jon and his slaves work with. Kept in one place so the
 * literal directories are not scattered across the prompt files (and can later be made configurable).
 * The prompt templates use the {@code ${docs}} / {@code ${plan}} placeholders and {@link #resolve(String)}
 * fills them; {@link #PLAN_FILE} is a compile-time constant so it can also be used in {@code @Tool} texts.
 */
public final class PeonPaths {

    private PeonPaths() {}

    /** Docs tree — Jon's single source of truth (SOLL/To-Be). */
    public static final String DOCS_DIR = "docs";

    /** The plan file both slaves hand off through (the durable handover). */
    public static final String PLAN_FILE = "peon-plan/overview.md";

    /** Fill the {@code ${docs}} / {@code ${plan}} placeholders of a prompt template from the constants. */
    public static String resolve(String template) {
        if (template == null) return null;
        return template
                .replace("${docs}", DOCS_DIR)
                .replace("${plan}", PLAN_FILE);
    }
}
