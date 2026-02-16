package org.sterl.llmpeon.parts;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.e4.core.di.annotations.Optional;
import org.eclipse.e4.core.services.log.Logger;
import org.eclipse.e4.ui.di.Focus;
import org.eclipse.e4.ui.services.IServiceConstants;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.sterl.llmpeon.parts.config.LlmPreferenceInitializer;
import org.sterl.llmpeon.parts.llm.ChatService;
import org.sterl.llmpeon.parts.tools.ToolService;
import org.sterl.llmpeon.parts.widget.ChatWidget;

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
    private final IPreferenceChangeListener prefListener = event -> {
        if (parent != null && !parent.isDisposed()) {
            parent.getDisplay().asyncExec(this::applyConfig);
        }
    };

    @PostConstruct
    public void createPartControl(Composite parent) {
        this.parent = parent;
        parent.setLayout(new FillLayout());
        toolService = new ToolService();
        chatService = new ChatService(LlmPreferenceInitializer.buildWithDefaults(), toolService);
        chat = new ChatWidget(chatService, toolService, parent, SWT.NONE);
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

        if (o instanceof IFile f) {
            var ctx = toolService.getContext();
            ctx.setSelectedFile(f.getFullPath().toString());
            ctx.setCurrentProject(f.getProject());
        } else {
            if (chat != null)
                chat.append("Selection", "This is a selection of " + o.getClass());
        }
    }

    @Inject
    @Optional
    public void setSelection(@Named(IServiceConstants.ACTIVE_SELECTION) Object[] selectedObjects) {
        if (chat != null)
            chat.append("Selection", "This is a multiple selection of " + selectedObjects.length + " objects");
    }
}
