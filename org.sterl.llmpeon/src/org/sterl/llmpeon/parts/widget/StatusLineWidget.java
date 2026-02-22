package org.sterl.llmpeon.parts.widget;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

public class StatusLineWidget extends Composite {

    private final Label skillIcon;
    private final Label skillLabel;
    private final Label agentIcon;
    private final Label agentLabel;
    private final Label fileLabel;
    private final Label tokenLabel;

    private final Color colorWarning;
    private final Color colorError;

    public StatusLineWidget(Composite parent, int style) {
        super(parent, style);

        var layout = new GridLayout(8, false);
        layout.marginHeight = 2;
        layout.marginWidth = 4;
        layout.horizontalSpacing = 4;
        setLayout(layout);
        setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        colorWarning = new Color(180, 130, 0);
        colorError = new Color(200, 0, 0);
        addDisposeListener(e -> {
            colorWarning.dispose();
            colorError.dispose();
        });

        var images = PlatformUI.getWorkbench().getSharedImages();

        // Skills icon + label
        skillIcon = new Label(this, SWT.NONE);
        skillIcon.setImage(images.getImage(ISharedImages.IMG_OBJ_ELEMENT));
        skillIcon.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

        skillLabel = new Label(this, SWT.NONE);
        skillLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

        // Separator
        var sep1 = new Label(this, SWT.SEPARATOR | SWT.VERTICAL);
        sep1.setLayoutData(gridHeight(16));

        // Agent icon + label
        agentIcon = new Label(this, SWT.NONE);
        agentIcon.setImage(images.getImage(ISharedImages.IMG_OBJ_ELEMENT));
        agentIcon.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

        agentLabel = new Label(this, SWT.NONE);
        agentLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

        // Separator
        var sep2 = new Label(this, SWT.SEPARATOR | SWT.VERTICAL);
        sep2.setLayoutData(gridHeight(16));

        // File label
        fileLabel = new Label(this, SWT.NONE);
        fileLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Token label (right-aligned)
        tokenLabel = new Label(this, SWT.RIGHT);
        tokenLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
    }

    public void update(int tokenUsed, int tokenMax, int skillCount, boolean hasAgentsMd, String selectedResource) {
        // Skills
        skillIcon.setVisible(skillCount > 0);
        ((GridData) skillIcon.getLayoutData()).exclude = skillCount == 0;
        skillLabel.setText(skillCount + " skill" + (skillCount != 1 ? "s" : ""));

        // Agent
        agentIcon.setVisible(hasAgentsMd);
        ((GridData) agentIcon.getLayoutData()).exclude = !hasAgentsMd;
        agentLabel.setText(hasAgentsMd ? "AGENTS.md" : "");
        agentLabel.setVisible(hasAgentsMd);
        ((GridData) agentLabel.getLayoutData()).exclude = !hasAgentsMd;

        // File
        fileLabel.setText(extractFileName(selectedResource));

        // Tokens
        int pct = tokenMax > 0 ? (tokenUsed * 100) / tokenMax : 0;
        tokenLabel.setText(tokenUsed + " / " + tokenMax + " - " + pct + "%");

        if (pct >= 92) {
            tokenLabel.setForeground(colorError);
        } else if (pct >= 80) {
            tokenLabel.setForeground(colorWarning);
        } else {
            tokenLabel.setForeground(null); // default
        }

        layout(true);
    }

    private static String extractFileName(String resource) {
        if (resource == null || resource.isEmpty()) return "";
        int sep = resource.lastIndexOf('/');
        if (sep < 0) sep = resource.lastIndexOf('\\');
        return sep >= 0 ? resource.substring(sep + 1) : resource;
    }

    private static GridData gridHeight(int height) {
        var gd = new GridData(SWT.CENTER, SWT.CENTER, false, false);
        gd.heightHint = height;
        return gd;
    }
}
