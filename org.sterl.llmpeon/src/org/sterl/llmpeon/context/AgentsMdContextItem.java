package org.sterl.llmpeon.context;

import java.util.ArrayList;
import java.util.Optional;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;

import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.IoUtils;
import org.sterl.llmpeon.parts.shared.JdtUtil;

/**
 * Context item that renders the base {@code AGENTS.md} and agent-specific {@code AGENTS-<agent>.md}
 * files. Silently skips missing files. Agent name is evaluated lazily at render time.
 */
public class AgentsMdContextItem implements ContextItem {

    private final String agentName;

    public AgentsMdContextItem(String agentName) {
        this.agentName = agentName;
    }

    @Override
    public String label() {
        return "AGENTS.md";
    }

    @Override
    public String render() {
        IProject project = EclipseUtil.firstOpenOrSelectedProject();
        if (project == null || !project.isAccessible()) return "";

        var parts = new ArrayList<String>();

        // Base AGENTS.md
        loadAgentFile(project, resolveBaseNames()).ifPresent(parts::add);

        // Agent-specific AGENTS-<agent>.md
        if (agentName != null && !agentName.isBlank()) {
            String key = resolveAgentKey(agentName);
            loadAgentFile(project, resolveAgentNames(key)).ifPresent(parts::add);
        }

        if (parts.isEmpty()) return "";
        return String.join("\n\n", parts);
    }

    private Optional<String> loadAgentFile(IProject project, String[] names) {
        for (String name : names) {
            Optional<IFile> file = EclipseUtil.findMember(project, name);
            if (file.isPresent() && file.get().exists()) {
                String text = IoUtils.readString(file.get());
                return Optional.of(JdtUtil.pathOf(file.get()) + ":\n---\n" + text);
            }
        }
        return Optional.empty();
    }

    private String[] resolveBaseNames() {
        return new String[] { "AGENTS.MD", "AGENTS.md", "Agents.md", "agents.md",
                              "RULES.md", "rules.md", "AGENT.md", "CLAUDE.md", "claude.md" };
    }

    private String[] resolveAgentNames(String key) {
        var names = new ArrayList<String>();
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

    private String resolveAgentKey(String agentName) {
        if (agentName.startsWith("Peon-")) {
            return agentName.substring(5).toUpperCase();
        }
        return agentName;
    }
}
