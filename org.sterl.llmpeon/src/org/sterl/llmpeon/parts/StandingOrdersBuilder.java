package org.sterl.llmpeon.parts;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.JdtUtil;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * Assembles the standing-orders system messages that are prepended each call.
 * Includes the selected resource context, any AGENTS.md content, and a plan
 * file hint when in AGENT mode.
 */
public class StandingOrdersBuilder {

    public interface MessageProvider extends Supplier<ChatMessage> {}
    
    private final Set<MessageProvider> providers = new LinkedHashSet<StandingOrdersBuilder.MessageProvider>();
    
    public StandingOrdersBuilder() {
        super();
    }
    public StandingOrdersBuilder add(MessageProvider provider) {
        providers.add(provider);
        return this;
    }
    
    public List<ChatMessage> build(
            IProject selectedProject,
            IResource selectedResource) {
        
        var result = new ArrayList<ChatMessage>();
        var msg = buildSelectedMessage(selectedProject, selectedResource);
        if (msg != null) result.add(msg);
        
        for (var p : providers) {
            msg = p.get();
            if (msg != null) result.add(msg);
        }
        
        return result;
    }
    
    ChatMessage buildSelectedMessage(
            IProject selectedProject,
            IResource selectedResource) {
        if (selectedProject == null && selectedResource == null) return null;
        
        var sb = new StringBuilder();
        if (selectedProject != null) {
            sb.append("Select in Eclipse:\n");
            sb.append(EclipseUtil.projectInfo(selectedProject));
        }
        if (selectedResource != null) {
            sb.append("\nFile selected: ").append(JdtUtil.pathOf(selectedResource));
            sb.append("\nDisk path: ").append(JdtUtil.diskPathOf(selectedResource));
        }
        return UserMessage.from(sb.toString());
    }
}
