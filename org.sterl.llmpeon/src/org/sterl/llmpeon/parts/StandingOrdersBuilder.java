package org.sterl.llmpeon.parts;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.template.TemplateContext;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * Assembles the standing-orders system messages that are prepended each call.
 * Includes the selected resource context, any AGENTS.md content, and a plan
 * file hint when in AGENT mode.
 */
public class StandingOrdersBuilder {

    public interface MessageProvider extends Function<TemplateContext, ChatMessage> {}
    
    private final TemplateContext context;
    private final Set<MessageProvider> providers = new LinkedHashSet<StandingOrdersBuilder.MessageProvider>();
    
    public StandingOrdersBuilder(TemplateContext context) {
        super();
        this.context = context;
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
            msg = p.apply(context);
            if (msg != null) result.add(msg);
        }
        
        return result;
    }
    
    ChatMessage buildSelectedMessage(IProject selectedProject,
            IResource selectedResource) {
        if (selectedProject == null && selectedResource == null) return null;
        
        String msg = "Selected:";
        if (selectedProject != null) {
            msg += "\nEclipse project: " + selectedProject.getName();
            msg += "\nDisk path: " + JdtUtil.diskPathOf(selectedProject);
        }
        if (selectedResource != null) {
            msg += "\nFile selected: " + JdtUtil.pathOf(selectedResource);
            msg += "\nDisk path: " + JdtUtil.diskPathOf(selectedResource);
        }
        return UserMessage.from(msg);
    }
}
