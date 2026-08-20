package org.sterl.llmpeon.context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import org.eclipse.core.resources.IProject;

/**
 * Resolver for the AGENTS.md context items: returns up to two {@link EclipseFileContextItem}s —
 * the base {@code AGENTS.md} and the agent-specific {@code AGENTS-<agent>.md} — each carrying its
 * own full workspace path as label and the exact ADR-0029 header as dedup key.
 * A missing project/file simply yields no item (nothing to inject, no status line).
 */
public final class AgentsMdContextItem extends EclipseFileContextItem {

    public static final String[] AGENT_FILES = { "AGENTS.MD", "AGENTS.md", "Agents.md", "agents.md",
            "RULES.md", "rules.md", "AGENT.md", "CLAUDE.md", "claude.md" };

    public AgentsMdContextItem(Supplier<IProject> project) {
        super(AGENT_FILES, project);
    }
    
    
    /**
     * Resolves the AGENTS.md context items for the given agent and project.
     * @return 0, 1 or 2 items — the base file and/or the agent file; missing files are absent.
     */
    public static List<ContextItem> itemsFor(String agentName, Supplier<IProject> project) {
        if (project == null) return List.of();

        ContextItem forAgent = new EclipseFileContextItem(resolveAgentNames(agentName), project);
        return Arrays.asList(new AgentsMdContextItem(project), forAgent);
    }

    private static String[] resolveAgentNames(String key) {
        var names = new ArrayList<String>();
        key = resolveAgentKey(key);
        names.add("AGENTS-" + key + ".md");
        names.add("agents-" + key.toLowerCase() + ".md");

        String titleCase = Character.toUpperCase(key.charAt(0)) + key.substring(1).toLowerCase();
        if (!titleCase.equals(key) && !titleCase.equals(key.toLowerCase())) {
            names.add("AGENTS-" + titleCase + ".md");
        }

        String hyphenatedUpper = key.replace(' ', '-').toUpperCase();
        if (!hyphenatedUpper.equals(key)) names.add("AGENTS-" + hyphenatedUpper + ".md");
        String hyphenatedLower = key.replace(' ', '-').toLowerCase();
        if (!hyphenatedLower.equals(key.toLowerCase())) names.add("agents-" + hyphenatedLower + ".md");

        return names.toArray(String[]::new);
    }
    
    private static String resolveAgentKey(String agentName) {
        if (agentName.startsWith("Peon-")) {
            return agentName.substring(5).toUpperCase();
        }
        return agentName;
    }
}
