package org.sterl.llmpeon.parts.agentsmd;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.sterl.llmpeon.StandingOrdersBuilder.MessageProvider;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.IoUtils;
import org.sterl.llmpeon.parts.shared.JdtUtil;

public class AgentsMdService implements MessageProvider {

    private volatile IFile agentsMd;
    private volatile IProject currentProject;
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private volatile Supplier<String> agentNameSupplier;
    
    @Override
    public List<String> get() {
        if (!enabled.get()) return List.of();

        var result = new ArrayList<String>();

        // Base AGENTS.md
        if (agentsMd != null && agentsMd.exists()) {
            result.add(loadAgentMd(agentsMd));
        }

        // Agent-specific AGENTS-<agent>.md
        if (agentNameSupplier != null && currentProject != null) {
            String agentName = agentNameSupplier.get();
            if (agentName != null && !agentName.isBlank()) {
                String key = resolveAgentKey(agentName);
                IFile file = resolveAgentSpecificFile(currentProject, key);
                if (file != null && file.exists()) {
                    result.add(loadAgentMd(file));
                }
            }
        }

        return result;
    }
    
    /**
     * The <b>base</b> {@code AGENTS.md} ground rules only — deliberately <b>without</b> the
     * agent-specific {@code AGENTS-<agent>.md}, which is keyed to the active agent name and would be
     * the wrong file for a delegated slave (Jon's key is {@code PO}, not his slaves'). Used to inject
     * the shared ground rules into Jon's RAM slaves. Returns {@code null} when disabled or no base file.
     */
    public String getBaseAgentsMd() {
        if (!enabled.get()) return null;
        if (agentsMd != null && agentsMd.exists()) return loadAgentMd(agentsMd);
        return null;
    }

    private String loadAgentMd(IFile file) {
        var text = IoUtils.readString(file);
        return JdtUtil.pathOf(file) + ":" + System.lineSeparator()
                + "---" + System.lineSeparator() + System.lineSeparator()
                + text;
    }
    

    /**
     * Resolves the agent name key for file lookup.
     * Built-in agents: "Peon-Dev" → "DEV", "Peon-Plan" → "PLAN".
     * Custom agents: use the display name as-is.
     */
    private String resolveAgentKey(String agentName) {
        if (agentName.startsWith("Peon-")) {
            return agentName.substring(5).toUpperCase();
        }
        return agentName;
    }

    /**
     * Resolves the agent-specific AGENTS-<key>.md file with case-insensitive fallback.
     */
    private IFile resolveAgentSpecificFile(IProject project, String key) {
        // Build fallback names in priority order
        var names = new ArrayList<String>();
        names.add("AGENTS-" + key + ".md");
        names.add("agents-" + key.toLowerCase() + ".md");

        // Title case (only if different from uppercase and lowercase)
        String titleCase = Character.toUpperCase(key.charAt(0)) + key.substring(1).toLowerCase();
        if (!titleCase.equals(key) && !titleCase.equals(key.toLowerCase())) {
            names.add("AGENTS-" + titleCase + ".md");
        }

        // Blanks replaced with hyphens
        String hyphenatedUpper = key.replace(' ', '-').toUpperCase();
        if (!hyphenatedUpper.equals(key)) {
            names.add("AGENTS-" + hyphenatedUpper + ".md");
        }
        String hyphenatedLower = key.replace(' ', '-').toLowerCase();
        if (!hyphenatedLower.equals(key.toLowerCase())) {
            names.add("agents-" + hyphenatedLower + ".md");
        }

        for (String n : names) {
            var r = EclipseUtil.findMember(project, n);
            if (r.isPresent()) return r.get();
        }
        return null;
    }

    public void setEnabled(boolean value) {
        enabled.set(value);
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    /**
     * Sets the callback that provides the currently active agent name.
     * Evaluated at {@link #get()} time, so agent switches are reflected immediately.
     */
    public void setAgentNameSupplier(Supplier<String> agentNameSupplier) {
        this.agentNameSupplier = agentNameSupplier;
    }

    /** Loads the AGENTS.md / agents.md content for the given path. */
    public boolean load(IProject inProject) {
        if (inProject == null) {
            agentsMd = null;
            currentProject = null;
            return false;
        }
        agentsMd = resolveFile(inProject).orElse(null);
        currentProject = inProject;

        return hasAgentFile();
    }

    /** Returns the discovered agent filename (e.g. "AGENTS.md"), or <code>null</code> if none found or not avtive. */
    public String getAgentFileName() {
        return agentsMd == null ? null : agentsMd.getName();
    }

    public boolean hasAgentFile() {
        return agentsMd != null;
    }

    static final String NAMES[] = {
            "AGENTS.MD",
            "AGENTS.md",
            "Agents.md",
            "agents.md",
            "RULES.md",
            "rules.md",
            "AGENT.md",
            "CLAUDE.md",
            "claude.md"
    };
    private Optional<IFile> resolveFile(IProject project) {
        if (project == null) return Optional.empty();
        for (String n : NAMES) {
            var r = EclipseUtil.findMember(project, n);
            if (r.isPresent()) return r;
        }
        return Optional.empty();
    }
}
