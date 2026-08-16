package org.sterl.llmpeon.context;

import java.util.ArrayList;
import java.util.Optional;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.jspecify.annotations.Nullable;

import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.IoUtils;
import org.sterl.llmpeon.parts.shared.JdtUtil;

/**
 * Context item that renders the base {@code AGENTS.md} and agent-specific {@code AGENTS-<agent>.md}
 * files. A missing project/file renders {@code null} (nothing to inject, no exception).
 * <p>
 * {@link #label()} and {@link #dedupKey()} are the full workspace path of the <b>base</b> file
 * (a substring of the rendered text); when the base file is missing, {@code dedupKey()} is
 * {@code null} and dedup falls back to rendered content.
 */
public class AgentsMdContextItem implements ContextItem {

    private final String agentName;
    private final IProject project;

    public AgentsMdContextItem(String agentName, IProject project) {
        this.agentName = agentName;
        this.project = project;
    }

    @Override
    public String label() {
        return baseFile().map(JdtUtil::pathOf).orElse("AGENTS.md");
    }

    @Override
    @Nullable
    public String dedupKey() {
        return baseFile().map(JdtUtil::pathOf).orElse(null);
    }

    @Override
    @Nullable
    public String render() {
        if (project == null || !project.isAccessible()) return null;

        var parts = new ArrayList<String>();
        loadAgentFile(resolveBaseNames()).ifPresent(parts::add);
        if (agentName != null && !agentName.isBlank()) {
            loadAgentFile(resolveAgentNames(resolveAgentKey(agentName))).ifPresent(parts::add);
        }
        if (parts.isEmpty()) return null;
        return String.join("\n\n", parts);
    }

    /** The base AGENTS.md file (first matching name), without reading its content. */
    private Optional<IFile> baseFile() {
        if (project == null || !project.isAccessible()) return Optional.empty();
        for (String name : resolveBaseNames()) {
            var file = EclipseUtil.findMember(project, name);
            if (file.isPresent() && file.get().exists()) return file;
        }
        return Optional.empty();
    }

    private Optional<String> loadAgentFile(String[] names) {
        for (String name : names) {
            var file = EclipseUtil.findMember(project, name);
            if (file.isPresent() && file.get().exists()) {
                return Optional.of(JdtUtil.pathOf(file.get()) + ":\n---\n" + IoUtils.readString(file.get()));
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
