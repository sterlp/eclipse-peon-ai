package org.sterl.llmpeon.parts.agentsmd;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.sterl.llmpeon.StandingOrdersBuilder.ContextItemProvider;
import org.sterl.llmpeon.context.AgentsMdContextItem;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.parts.shared.EclipseUtil;

/**
 * Thin provider for the AGENTS.md context: holds the toggle and the current project and delegates
 * the actual file resolution/rendering to {@link AgentsMdContextItem}.
 */
public class AgentsMdService implements ContextItemProvider {

    private volatile IFile agentsMd;
    private volatile IProject currentProject;
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private volatile Supplier<String> agentNameSupplier;

    @Override
    public List<ContextItem> get() {
        if (!enabled.get() || currentProject == null) return List.of();
        String agentName = agentNameSupplier == null ? null : agentNameSupplier.get();
        return AgentsMdContextItem.itemsFor(agentName, currentProject);
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

    /** Remembers the project and the discovered base file (for {@link #getAgentFileName()}). */
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

    /** Returns the discovered agent filename (e.g. "AGENTS.md"), or <code>null</code> if none found or not active. */
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
