package org.sterl.llmpeon.parts.widget;

import java.net.URI;
import java.util.function.Consumer;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

public class StatusLineWidget extends Composite {

    private final Button btnPin;
    private final Label projectLabel;
    private final Label sep0;
    private final Label skillIcon;
    private final Label skillLabel;
    private final Label agentIcon;
    private final Label agentLabel;
    private final Label fileLabel;

    public StatusLineWidget(Composite parent, int style, Consumer<Boolean> onPinChange) {
        super(parent, style);
        setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        RowLayout rowLayout = new RowLayout(SWT.HORIZONTAL);
        rowLayout.wrap = false;
        rowLayout.pack = true;
        rowLayout.center = true;
        rowLayout.marginTop = 2;
        rowLayout.marginBottom = 2;
        rowLayout.spacing = 4;
        setLayout(rowLayout);

        // --- Pin button (hidden until a project is selected) ---
        btnPin = new Button(this, SWT.TOGGLE);
        btnPin.setToolTipText("Pin project — navigation won't change the active project");
        btnPin.setEnabled(false);
        try {
            Image pinImage = ImageDescriptor
                .createFromURL(URI.create(
                    "platform:/plugin/org.eclipse.jdt.ui/icons/full/elcl16/pin_view.svg").toURL())
                .createImage();
            btnPin.setImage(pinImage);
            btnPin.addDisposeListener(e -> pinImage.dispose());
        } catch (Exception ex) {
            btnPin.setText("Pin");
        }
        btnPin.addListener(SWT.Selection, e -> onPinChange.accept(btnPin.getSelection()));

        // --- Project label (hidden until a project is selected) ---
        projectLabel = new Label(this, SWT.NONE);
        projectLabel.setVisible(false);

        // --- Separator after project area (hidden when no project) ---
        sep0 = new Label(this, SWT.SEPARATOR | SWT.VERTICAL);
        sep0.setLayoutData(new RowData(SWT.DEFAULT, 16));
        sep0.setVisible(false);

        var images = PlatformUI.getWorkbench().getSharedImages();

        // --- Skills icon (hidden when 0 skills) ---
        skillIcon = new Label(this, SWT.NONE);
        skillIcon.setImage(images.getImage(ISharedImages.IMG_OBJ_FOLDER));
        skillIcon.setVisible(false);

        skillLabel = new Label(this, SWT.NONE);

        // --- Separator ---
        var sep1 = new Label(this, SWT.SEPARATOR | SWT.VERTICAL);
        sep1.setLayoutData(new RowData(SWT.DEFAULT, 16));

        // --- Agent icon + label (hidden when no AGENTS.md) ---
        agentIcon = new Label(this, SWT.NONE);
        agentIcon.setImage(images.getImage(ISharedImages.IMG_OBJ_FILE));
        agentIcon.setVisible(false);

        agentLabel = new Label(this, SWT.NONE);
        agentLabel.setVisible(false);

        // --- Separator ---
        var sep2 = new Label(this, SWT.SEPARATOR | SWT.VERTICAL);
        sep2.setLayoutData(new RowData(SWT.DEFAULT, 16));

        // --- File label ---
        fileLabel = new Label(this, SWT.NONE);
    }

    public void update(int skillCount, boolean hasAgentsMd, IProject project, IResource selectedResource) {

        // --- Pin + project ---
        boolean hasProject = project != null;
        btnPin.setEnabled(hasProject);
        projectLabel.setVisible(hasProject);
        sep0.setVisible(hasProject);
        if (hasProject) {
            projectLabel.setText(project.getName());
        }

        // --- Skills ---
        skillIcon.setVisible(skillCount > 0);
        skillLabel.setText(skillCount + " skill" + (skillCount != 1 ? "s" : ""));

        // --- Agent ---
        agentIcon.setVisible(hasAgentsMd);
        agentLabel.setVisible(hasAgentsMd);
        agentLabel.setText(hasAgentsMd ? "AGENTS.md" : "");

        // --- File ---
        fileLabel.setText(selectedResource != null ? selectedResource.getName() : "");

        layout(true);
        getParent().layout(new Control[] { this });
    }

    /** Sync the pin button state without firing the listener. */
    public void setPinned(boolean pinned) {
        if (!btnPin.isDisposed()) btnPin.setSelection(pinned);
    }
}
