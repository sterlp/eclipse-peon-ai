package org.sterl.llmpeon.parts.agentsmd;

import java.util.Optional;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.template.TemplateContext;

import dev.langchain4j.data.message.SystemMessage;

public class AgentsMdService {

    private IProject project;
    private Optional<IFile> agentsMd = Optional.empty();

    /**
     * Loads the AGENTS.md / agents.md content for the given path.
     * The path may point directly to the file or to a directory containing it.
     *
     * @return <code>true</code> if the content changed (including first load or path switch)
     */
    public boolean load(IProject inProject) {
        if (inProject == null) {
            agentsMd = Optional.empty();
            return false;
        }
        this.project = inProject;
        agentsMd = resolve();

        return hasAgentFile();
    }

    /** Returns a processed {@link SystemMessage} if an AGENTS.md file is present, empty otherwise. */
    public Optional<SystemMessage> agentMessage(TemplateContext context) {
        if (!hasAgentFile()) return Optional.empty();
        return Optional.of(SystemMessage.from(context.process(read())));
    }

    /** Returns the raw content, or empty string if not loaded / file not present. */
    public String read() {
        try {
            return agentsMd.isEmpty() ? "" : agentsMd.get().readString();
        } catch (CoreException e) {
            throw new RuntimeException("Failed to read " + agentsMd, e);
        }
    }

    public boolean hasAgentFile() {
        return agentsMd.isPresent();
    }

    // -------------------------------------------------------------------------

    static final String NAMES[] = {
            "AGENTS.MD",
            "AGENTS.md",
            "Agents.md",
            "agents.md",
            "rules.md",
    };
    private Optional<IFile> resolve() {
        if (project == null) return null;
        for (String n : NAMES) {
            var r = EclipseUtil.findMember(project, n);
            if (r.isPresent()) return r;
        }
        return Optional.empty();
    }
}
