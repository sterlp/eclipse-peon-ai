package org.sterl.llmpeon.parts;

import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.e4.core.di.annotations.Optional;
import org.eclipse.e4.core.services.log.Logger;
import org.eclipse.e4.ui.di.Focus;
import org.eclipse.e4.ui.services.IServiceConstants;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.sterl.llmpeon.ai.ChatService;
import org.sterl.llmpeon.parts.config.LlmPreferenceInitializer;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.tools.EclipseBuildTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceFilesTool;
import org.sterl.llmpeon.parts.widget.ChatWidget;
import org.sterl.llmpeon.tool.DiskFilesTool;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.WebFetchTool;

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
    private final ToolService toolService = new ToolService();
    private final EclipseWorkspaceFilesTool workspaceFilesTool = new EclipseWorkspaceFilesTool();

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
        toolService.addTool(workspaceFilesTool);
        toolService.addTool(new DiskFilesTool(ResourcesPlugin.getWorkspace().getRoot().getRawLocation().toFile().toPath()));
        toolService.addTool(new WebFetchTool());
        toolService.addTool(new EclipseBuildTool());

        chatService = new ChatService(LlmPreferenceInitializer.buildWithDefaults(), toolService);
        chat = new ChatWidget(chatService, parent, SWT.NONE);
        applyConfig();

        if (logger != null)
            logger.info("We have a logger ... " + chatService.getConfig());

        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
        prefs.addPreferenceChangeListener(prefListener);
    }

    @PreDestroy
    public void dispose() {
        InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID).removePreferenceChangeListener(prefListener);
    }

    private void applyConfig() {
        var config = LlmPreferenceInitializer.buildWithDefaults();
        chatService.updateConfig(config);
        chat.refreshStatusLine();
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
    public void setTextSelection(
            @Named(IServiceConstants.ACTIVE_SELECTION) ITextSelection ts) {
        if (chat != null) chat.setTextSelection(ts);
    }

    @Inject
    @Optional
    public void setSelection(@Named(IServiceConstants.ACTIVE_SELECTION) Object o) {
        if (o instanceof ISelection) return;

        IResource selection = null;
        if (o instanceof ICompilationUnit cu) {
            selection = cu.getResource();
        } else if (o instanceof IFile f) {
            selection = f;
        } else if (o instanceof IResource f) {
            selection = f;
        }
        contextFile.set(selection == null ? null : selection.getFullPath().toPortableString());
        workspaceFilesTool.setCurrentProject(EclipseUtil.resolveProject(selection));
        if (chat != null) Display.getDefault().asyncExec(() -> chat.updateContextFile(contextFile.get()));
    }

    @Inject
    @Optional
    public void setSelection(@Named(IServiceConstants.ACTIVE_SELECTION) Object[] selectedObjects) {
        // TODO what we do with this?
    }
}
