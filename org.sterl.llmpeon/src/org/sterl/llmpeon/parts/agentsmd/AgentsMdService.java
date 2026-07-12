package org.sterl.llmpeon.parts.agentsmd;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.sterl.llmpeon.StandingOrdersBuilder.MessageProvider;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.IoUtils;
import org.sterl.llmpeon.parts.shared.JdtUtil;

public class AgentsMdService implements MessageProvider {

    private volatile IFile agentsMd;
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    
    @Override
    public String get() {
        if (agentsMd == null || !agentsMd.exists()) return null;
        var text = IoUtils.readString(agentsMd);
        return  JdtUtil.pathOf(agentsMd) + " full content:\n" + text;
    }

    public void setEnabled(boolean value) {
        enabled.set(value);
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    /** Loads the AGENTS.md / agents.md content for the given path. */
    public boolean load(IProject inProject) {
        if (inProject == null) {
            agentsMd = null;
            return false;
        }
        agentsMd = resolveFile(inProject).orElse(null);

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
