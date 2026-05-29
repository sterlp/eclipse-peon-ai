package org.sterl.llmpeon.parts.agentsmd;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.templates.TemplateContext;
import org.sterl.llmpeon.StandingOrdersBuilder.MessageProvider;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.JdtUtil;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;

public class AgentsMdService implements MessageProvider {

    private static final String HEADER = 
            "%s\n" +
            "Use this file for critical, non-obvious, always-needed project settings — like workspace memory, but scoped to this project. Edit it directly. Keep it very short, and update or clean up entries as work evolves so only current, relevant rules remain.\n" +
            "---\n";

    private IProject project;
    private final AtomicReference<IFile> agentsMd = new AtomicReference<>();
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    
    @Override
    public ChatMessage get() {
        if (!enabled.get() || agentsMd.get() == null || !agentsMd.get().exists()) return null;
        var f = agentsMd.get();
        try {
            var text = f.readString();
            return UserMessage.from(String.format(HEADER, JdtUtil.pathOf(f)) + "\n" + text);
        } catch (CoreException e) {
            throw new RuntimeException(e);
        }
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
            agentsMd.set(null);
            return false;
        }
        this.project = inProject;
        agentsMd.set(resolveFile().orElse(null));

        return hasAgentFile();
    }

    /** Returns a processed {@link AiMessage} with path header when enabled and file present, empty otherwise. */
    public Optional<AiMessage> agentMessage(TemplateContext context) {
        IFile file = agentsMd.get();
        if (file == null || !enabled.get()) return Optional.empty();
        String content;
        try {
            content = file.readString();
        } catch (CoreException e) {
            throw new RuntimeException("Failed to read " + file, e);
        }
        String fullText = String.format(HEADER, JdtUtil.pathOf(file)) + " content:\n" + content;
        return Optional.of(AiMessage.from(fullText));
    }

    /** Returns the discovered agent filename (e.g. "AGENTS.md"), or <code>null</code> if none found or not avtive. */
    public String getAgentFileName() {
        IFile file = agentsMd.get();
        return file == null ? null : file.getName();
    }

    public boolean hasAgentFile() {
        return agentsMd.get() != null;
    }

    static final String NAMES[] = {
            "AGENTS.MD",
            "AGENTS.md",
            "Agents.md",
            "agents.md",
            "rules.md",
            "AGENT.md",
    };
    private Optional<IFile> resolveFile() {
        if (project == null) return Optional.empty();
        for (String n : NAMES) {
            var r = EclipseUtil.findMember(project, n);
            if (r.isPresent()) return r;
        }
        return Optional.empty();
    }
}
