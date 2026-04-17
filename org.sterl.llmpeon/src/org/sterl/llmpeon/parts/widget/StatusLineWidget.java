package org.sterl.llmpeon.parts.widget;

import java.util.function.Consumer;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.sterl.llmpeon.shared.StringUtil;

/**
 * Status bar below the action bar. Shows project pin, selected file, skills
 * toggle, AGENTS.md indicator, MCP toggle, and the Compact button.
 * Uses a wrapping RowLayout so it reflows naturally on narrow views.
 */
public class StatusLineWidget extends Composite {

    /** Single toggle button — hidden when no project is selected. Text: "📌 ProjectName". */
    private final Button btnPin;
    private final Button btnSkills;
    private final Label agentIcon;
    private final Label agentLabel;
    private final Label fileLabel;
    private final Button btnMcp;
    private final Button btnCompress;

    private final Color colorWarning;
    private final Color colorError;

    public StatusLineWidget(Composite parent, int style,
            Consumer<Boolean> onPinChange,
            Consumer<Boolean> onSkillsToggle,
            Consumer<Boolean> onMcpToggle,
            Runnable onCompress) {
        super(parent, style);
        setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        colorWarning = new Color(180, 130, 0);
        colorError = new Color(200, 0, 0);
        addDisposeListener(e -> {
            colorWarning.dispose();
            colorError.dispose();
        });

        RowLayout rowLayout = new RowLayout(SWT.HORIZONTAL);
        rowLayout.wrap = true;
        rowLayout.pack = true;
        rowLayout.center = true;
        rowLayout.marginTop = 2;
        rowLayout.marginBottom = 2;
        rowLayout.marginLeft = 4;
        rowLayout.spacing = 4;
        setLayout(rowLayout);

        // --- Pin + project: single toggle button, hidden until a project is selected ---
        btnPin = new Button(this, SWT.TOGGLE);
        btnPin.setToolTipText("Pin project — navigation won't change the active project");
        RowData pinRd = new RowData();
        pinRd.exclude = true;
        btnPin.setLayoutData(pinRd);
        btnPin.setVisible(false);
        btnPin.addListener(SWT.Selection, e -> onPinChange.accept(btnPin.getSelection()));

        // --- Skills toggle ---
        btnSkills = new Button(this, SWT.TOGGLE);
        btnSkills.setSelection(true);
        btnSkills.setText("\u26A1 0 skills");
        btnSkills.setToolTipText("Toggle skills on/off");
        btnSkills.addListener(SWT.Selection, e -> onSkillsToggle.accept(btnSkills.getSelection()));

        var sep1 = new Label(this, SWT.SEPARATOR | SWT.VERTICAL);
        sep1.setLayoutData(new RowData(SWT.DEFAULT, 16));

        // --- Agent icon + label ---
        var images = PlatformUI.getWorkbench().getSharedImages();
        agentIcon = new Label(this, SWT.NONE);
        agentIcon.setImage(images.getImage(ISharedImages.IMG_OBJ_FILE));
        agentIcon.setVisible(false);

        agentLabel = new Label(this, SWT.NONE);
        agentLabel.setVisible(false);

        var sep2 = new Label(this, SWT.SEPARATOR | SWT.VERTICAL);
        sep2.setLayoutData(new RowData(SWT.DEFAULT, 16));

        // --- File label ---
        fileLabel = new Label(this, SWT.NONE);

        var sep3 = new Label(this, SWT.SEPARATOR | SWT.VERTICAL);
        sep3.setLayoutData(new RowData(SWT.DEFAULT, 16));

        // --- MCP toggle ---
        btnMcp = new Button(this, SWT.TOGGLE);
        btnMcp.setText("MCP off");
        btnMcp.setToolTipText("Enable MCP tools (configure via Window > Preferences > AI Peon MCP)");
        btnMcp.addListener(SWT.Selection, e -> onMcpToggle.accept(btnMcp.getSelection()));

        // --- Compact button ---
        btnCompress = new Button(this, SWT.PUSH);
        btnCompress.setText("Compact");
        btnCompress.setToolTipText("Compact conversation context");
        btnCompress.setEnabled(false);
        btnCompress.addListener(SWT.Selection, e -> onCompress.run());
    }

    public void update(int skillCount, boolean hasAgentsMd, IProject project, IResource selectedResource) {

        // --- Pin: show/hide the button with project name ---
        boolean hasProject = project != null;
        RowData pinRd = (RowData) btnPin.getLayoutData();
        if (pinRd.exclude == hasProject) { // visibility changed
            pinRd.exclude = !hasProject;
            btnPin.setVisible(hasProject);
        }
        if (hasProject) {
            btnPin.setText("\uD83D\uDCCC " + project.getName()); // 📌 ProjectName
        }

        // --- Agent ---
        agentIcon.setVisible(hasAgentsMd);
        agentLabel.setVisible(hasAgentsMd);
        agentLabel.setText(hasAgentsMd ? "AGENTS.md" : "");

        // --- File ---
        fileLabel.setText(selectedResource != null ? selectedResource.getName() : "");

        setSkillCount(skillCount);

        layout(true);
        getParent().layout(new Control[] { this });
    }

    /** Update the skills toggle button text with the loaded skill count. */
    public void setSkillCount(int count) {
        btnSkills.setText("\u26A1 " + count + " skill" + (count != 1 ? "s" : ""));
        btnSkills.getParent().layout(false, false);
    }

    /** Sync the skills toggle button state without firing the listener. */
    public void setSkillsEnabled(boolean enabled) {
        btnSkills.setSelection(enabled);
    }

    /** Sync the pin button state without firing the listener. */
    public void setPinned(boolean pinned) {
        if (!btnPin.isDisposed()) btnPin.setSelection(pinned);
    }

    /** Enable or disable the MCP button (disabled when no servers are configured). */
    public void setMcpAvailable(boolean available) {
        btnMcp.setEnabled(available);
        if (!available) btnMcp.setSelection(false);
    }

    /** Set the MCP toggle button state without firing the listener. */
    public void setMcpEnabled(boolean value) {
        btnMcp.setSelection(value);
        btnMcp.setText(value ? "MCP on" : "MCP off");
    }

    /** Returns whether the MCP toggle is currently on. */
    public boolean isMcpEnabled() {
        return btnMcp.getSelection();
    }

    /** Update the Compact button label and tooltip with current token usage. */
    public void updateCompact(int tokenUsed, int tokenMax) {
        int pct = tokenMax > 0 ? (tokenUsed * 100) / tokenMax : 0;
        if (pct >= 88) btnCompress.setForeground(colorError);
        else if (pct >= 70) btnCompress.setForeground(colorWarning);
        else btnCompress.setForeground(null);

        btnCompress.setText("Compact " + StringUtil.toK(tokenUsed) + "/" + StringUtil.toK(tokenMax));
        btnCompress.setToolTipText(tokenUsed + " / " + tokenMax + " tokens used (" + pct
                + "%, " + StringUtil.toK(tokenUsed) + "/" + StringUtil.toK(tokenMax) + ") — click to compact the conversation");
        btnCompress.getParent().layout(false, false);
        btnCompress.setEnabled(tokenUsed > 100);
    }
}
