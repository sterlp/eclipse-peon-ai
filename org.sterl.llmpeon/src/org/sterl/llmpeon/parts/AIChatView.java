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
import org.sterl.llmpeon.parts.tools.SelectedFileTool;
import org.sterl.llmpeon.parts.tools.UpdateFileTool;
import org.sterl.llmpeon.parts.widget.ChatWidget;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;

public class AIChatView {
    private ChatWidget chat;
    private Composite parent;
    @Inject Logger logger;
    private ChatService chatService;
    private final IPreferenceChangeListener prefListener = event -> {
        if (parent != null && !parent.isDisposed()) {
            parent.getDisplay().asyncExec(this::applyConfig);
        }
    };

    @PostConstruct
    public void createPartControl(Composite parent) {
        this.parent = parent;
        parent.setLayout(new FillLayout());
        chatService = new ChatService(LlmPreferenceInitializer.buildWithDefaults());
        chat = new ChatWidget(chatService, parent, SWT.NONE);
        if (logger != null) logger.info("We have a logger ... " + chatService.getConfig());

        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(LlmPreferenceConstants.PLUGIN_ID);
        prefs.addPreferenceChangeListener(prefListener);
    }

    @PreDestroy
    public void dispose() {
        InstanceScope.INSTANCE.getNode(LlmPreferenceConstants.PLUGIN_ID)
                .removePreferenceChangeListener(prefListener);
    }

    private void applyConfig() {
        chatService.updateConfig(LlmPreferenceInitializer.buildWithDefaults());
    }

    @Focus
    public void setFocus() {
        chat.setFocus();
    }

    /**
     * This method is kept for E3 compatibility. You can remove it if you do not mix
     * E3 and E4 code. <br/>
     * With E4 code you will set directly the selection in ESelectionService and you
     * do not receive a ISelection
     *
     * @param s the selection received from JFace (E3 mode)
     */
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

    /**
     * This method manages the selection of your current object. In this example we
     * listen to a single Object (even the ISelection already captured in E3 mode).
     * <br/>
     * You should change the parameter type of your received Object to manage your
     * specific selection
     *
     * @param o : the current object received
     */
    @Inject
    @Optional
    public void setSelection(@Named(IServiceConstants.ACTIVE_SELECTION) Object o) {

        // Remove the 2 following lines in pure E4 mode, keep them in mixed mode
        if (o instanceof ISelection) // Already captured
            return;

        if (o instanceof IFile f) {
            chatService.addTool(new SelectedFileTool(f));
            try {
                chatService.addTool(new UpdateFileTool(f.getLocation().toPath(),
                        java.nio.charset.Charset.forName(f.getCharset())));
            } catch (Exception e) {
                chatService.addTool(new UpdateFileTool(f.getLocation().toPath()));
            }
        } else {
        	if (chat != null)
        		chat.append("Selection", "This is a selection of " + o.getClass());        	
        }
    }

    /**
     * This method manages the multiple selection of your current objects. <br/>
     * You should change the parameter type of your array of Objects to manage your
     * specific selection
     *
     * @param o : the current array of objects received in case of multiple
     *          selection
     */
    @Inject
    @Optional
    public void setSelection(@Named(IServiceConstants.ACTIVE_SELECTION) Object[] selectedObjects) {

        // Test if label exists (inject methods are called before PostConstruct)
        if (chat != null)
            chat.append("Selection", "This is a multiple selection of " + selectedObjects.length + " objects");
    }
}
