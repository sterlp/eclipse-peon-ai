package org.sterl.llmpeon.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.sterl.llmpeon.shared.PromptYmlParser;
import org.sterl.llmpeon.shared.model.SimplePromptFile;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A user-defined custom agent loaded from {@code <agentDir>/AGENT.md}.
 *
 * <p>The markdown body becomes the agent's system prompt (appended to the default prompt). The
 * frontmatter adds three optional fields on top of {@code name}/{@code description}:</p>
 * <ul>
 *   <li>{@code tools} — allowlist of tool-name prefixes ({@code *} = all). {@code null} means the
 *       field was absent and the agent may use <b>all</b> tools.</li>
 *   <li>{@code readOnly} — when {@code true} only non-edit tools
 *       ({@code SmartTool.isEditTool() == false}) are offered.</li>
 *   <li>{@code model} — overrides the active model for this agent.</li>
 * </ul>
 */
@Getter
@SuperBuilder(toBuilder = true)
public class AgentPromptFile extends SimplePromptFile {

    /** {@code null} = field absent = all tools allowed. */
    private final List<String> tools;
    private final boolean readOnly;
    /** Mutable so a model change can be persisted back without re-parsing the file. */
    @Setter
    private volatile String model;
    /** Directory containing the AGENT.md, for future resource resolution. */
    private final Path agentDir;

    /**
     * Parses an {@code AGENT.md}. On top of {@code name}/{@code description} it reads the
     * agent-specific fields {@code tools} (block list or inline CSV — absent means all tools),
     * {@code readOnly}/{@code read-only} and {@code model}. Returns {@code null} for a non-regular
     * file.
     */
    public static AgentPromptFile parse(Path agentFile) throws IOException {
        if (!Files.isRegularFile(agentFile)) return null;

        var fm = PromptYmlParser.parseFrontmatter(agentFile);
        String readOnlyRaw = PromptYmlParser.firstOrDefault(fm, "readonly",
                PromptYmlParser.firstOrDefault(fm, "read-only", null));

        return AgentPromptFile.builder()
                .name(PromptYmlParser.firstOrDefault(fm, "name", PromptYmlParser.defaultSkillName(agentFile)))
                .description(PromptYmlParser.firstOrDefault(fm, "description", null))
                .promptFile(agentFile)
                .tools(PromptYmlParser.toolAllowlist(fm.get("tools")))
                .readOnly("true".equalsIgnoreCase(readOnlyRaw))
                .model(PromptYmlParser.firstOrDefault(fm, "model", null))
                .agentDir(agentFile.getParent())
                .build();
    }
}
