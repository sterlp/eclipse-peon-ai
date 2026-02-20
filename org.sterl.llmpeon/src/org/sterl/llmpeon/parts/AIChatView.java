package org.sterl.llmpeon.parts;

import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.e4.core.di.annotations.Optional;
import org.eclipse.e4.core.services.log.Logger;
import org.eclipse.e4.ui.di.Focus;
import org.eclipse.e4.ui.services.IServiceConstants;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.sterl.llmpeon.ai.ChatService;
import org.sterl.llmpeon.parts.config.LlmPreferenceInitializer;
import org.sterl.llmpeon.parts.tools.CreateFileTool;
import org.sterl.llmpeon.parts.tools.EclipseToolContext;
import org.sterl.llmpeon.parts.tools.ReadFileTool;
import org.sterl.llmpeon.parts.tools.ReadSelectedFileTool;
import org.sterl.llmpeon.parts.tools.SearchFilesTool;
import org.sterl.llmpeon.parts.tools.UpdateFileTool;
import org.sterl.llmpeon.parts.tools.UpdateSelectedFileTool;
import org.sterl.llmpeon.parts.widget.ChatWidget;
import org.sterl.llmpeon.tool.ToolService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;

public class AIChatView {
    private ChatWidget chat;
    private Composite parent;
    @Inject
    Logger logger;
    private ChatService chatService;
    private ToolService toolService;
    private EclipseToolContext toolContext;
    
    final AtomicReference<String> contextFile = new AtomicReference<String>();

    private final IPreferenceChangeListener prefListener = event -> {
        if (parent != null && !parent.isDisposed()) {
            parent.getDisplay().asyncExec(this::applyConfig);
        }
    };

    @PostConstruct
    public void createPartControl(Composite parent) {
        this.parent = parent;
        parent.setLayout(new FillLayout());
        toolContext = new EclipseToolContext();
        toolService = new ToolService();
        toolService.addTool(new CreateFileTool(toolContext));
        toolService.addTool(new ReadFileTool(toolContext));
        toolService.addTool(new ReadSelectedFileTool(toolContext));
        toolService.addTool(new SearchFilesTool(toolContext));
        toolService.addTool(new UpdateFileTool(toolContext));
        toolService.addTool(new UpdateSelectedFileTool(toolContext));
        
        
        chatService = new ChatService(LlmPreferenceInitializer.buildWithDefaults(), toolService);

        chat = new ChatWidget(chatService, parent, SWT.NONE);
        chat.updateContextLabel(contextFile.get());
        if (logger != null)
            logger.info("We have a logger ... " + chatService.getConfig());

        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(LlmPreferenceConstants.PLUGIN_ID);
        prefs.addPreferenceChangeListener(prefListener);
    }

    @PreDestroy
    public void dispose() {
        InstanceScope.INSTANCE.getNode(LlmPreferenceConstants.PLUGIN_ID).removePreferenceChangeListener(prefListener);
    }

    private void applyConfig() {
        chatService.updateConfig(LlmPreferenceInitializer.buildWithDefaults());
    }

    @Focus
    public void setFocus() {
        chat.setFocus();
    }

    @Inject
    @Optional
    public void setSelection(@Named(IServiceConstants.ACTIVE_SELECTION) ISelection s) {
        if (s == null || s.isEmpty())
            return;

        if (s instanceof IStructuredSelection) {
            IStructuredSelection iss = (IStructuredSelection) s;
            if (iss.size() == 1)
                setSelection(iss.getFirstElement());
            else
                setSelection(iss.toArray());
        }
    }

    @Inject
    @Optional
    public void setSelection(@Named(IServiceConstants.ACTIVE_SELECTION) Object o) {
        if (o instanceof ISelection) return;

        if (o instanceof ICompilationUnit cu) {
            IFile f = (IFile) cu.getResource();
            var ctx = toolContext;
            if (f != null) {
                ctx.setSelectedFile(f.getFullPath().toString());
                ctx.setCurrentProject(f.getProject());
                contextFile.set(f.getFullPath().toOSString());
            } else {
                System.err.println("ICompilationUnit without a IFile " + cu.getPath().toOSString());
                ctx.setSelectedFile(cu.getPath().toOSString());
                contextFile.set(cu.getPath().toOSString());
            }
        } else if (o instanceof IFile f) {
            var ctx = toolContext;
            ctx.setSelectedFile(f.getFullPath().toString());
            ctx.setCurrentProject(f.getProject());
            contextFile.set(f.getFullPath().toOSString());
            
            System.err.println("toOSString");
            System.err.println(f.getFullPath().toOSString());
            System.err.println(ResourcesPlugin.getWorkspace().getRoot().getFile(IPath.fromOSString(f.getFullPath().toOSString())).exists());
            System.err.println("getFullPath");
            System.err.println(f.getFullPath().toString());
            System.err.println(ResourcesPlugin.getWorkspace().getRoot().getFile(IPath.fromOSString(f.getFullPath().toString())).exists());
            System.err.println("toPortableString");
            System.err.println(f.getFullPath().toPortableString());
            System.err.println(ResourcesPlugin.getWorkspace().getRoot().getFile(IPath.fromOSString(f.getFullPath().toPortableString())).exists());
            
            System.err.println("getRawLocation");
            System.err.println(f.getRawLocation().toPortableString());
            System.err.println(f.getRawLocation().toString());
        }
        if (chat != null) Display.getDefault().asyncExec(() -> chat.updateContextLabel(contextFile.get()));
    }

    @Inject
    @Optional
    public void setSelection(@Named(IServiceConstants.ACTIVE_SELECTION) Object[] selectedObjects) {
        if (chat != null)
            chat.append("Selection", "This is a multiple selection of " + selectedObjects.length + " objects");
    }
}
