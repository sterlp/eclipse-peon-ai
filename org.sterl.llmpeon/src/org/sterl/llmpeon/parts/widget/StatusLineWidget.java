package org.sterl.llmpeon.parts.widget;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.sterl.llmpeon.parts.shared.EclipseUiUtil;
import org.sterl.llmpeon.parts.shared.ImageUtil;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.skill.SkillPromptFile;

/**
 * Status bar below the action bar. Shows project pin, selected file, skills
 * toggle, AGENTS.md indicator, MCP toggle, and the Compact button.
 * Uses a wrapping RowLayout so it reflows naturally on narrow views.
 */
public class StatusLineWidget extends Composite {

    /** Single toggle button — hidden when no project is selected. Text: "📌 ProjectName". */
    private final Button btnPin;
    private final Button btnSkills;
    private final Button btnAgentsMd;
    private final Label fileLabel;
    private final Control fileSeparator;
    private final Button btnMcp;
    private final Button btnCompress;

    private final Color colorWarning;
    private final Color colorError;
    private Supplier<List<SkillPromptFile>> skillsProvider;
    private Consumer<SkillMenuSelection> onSkillMenuChange;

    private final ISharedImages images = PlatformUI.getWorkbench().getSharedImages();

    public StatusLineWidget(Composite parent, int style,
            Consumer<Boolean> onPinChange,
            Consumer<Boolean> onSkillsToggle,
            Consumer<Boolean> onMcpToggle,
            Consumer<Boolean> onAgentsMdToggle,
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
        final var imgPinned = ImageUtil.loadImage(this, ImageUtil.PIN);
        btnPin = new Button(this, SWT.TOGGLE);
        btnPin.setImage(imgPinned);
        btnPin.setToolTipText("Pin project — navigation won't change the active project");
        RowData pinRd = new RowData();
        pinRd.exclude = true;
        btnPin.setLayoutData(pinRd);
        btnPin.setVisible(false);
        btnPin.addListener(SWT.Selection, e -> onPinChange.accept(btnPin.getSelection()));
        
        // --- File label ---
        fileLabel = new Label(this, SWT.NONE);
        fileLabel.setToolTipText("Automatically included in chat context — the AI sees this file");
        RowData fileRd = new RowData();
        fileRd.exclude = true;
        fileLabel.setLayoutData(fileRd);
        fileLabel.setVisible(false);

        fileSeparator = EclipseUiUtil.newSeparator(this);
        RowData fileSeparatorRd = new RowData();
        fileSeparatorRd.exclude = true;
        fileSeparator.setLayoutData(fileSeparatorRd);
        fileSeparator.setVisible(false);

        // --- AGENTS.md toggle button (disabled when no agent file found) ---
        btnAgentsMd = new Button(this, SWT.TOGGLE);
        btnAgentsMd.setImage(images.getImage(ISharedImages.IMG_OBJ_FILE));
        btnAgentsMd.setText("AGENTS.md");
        btnAgentsMd.setSelection(true);
        btnAgentsMd.setEnabled(true);
        btnAgentsMd.setToolTipText("Toggle AGENTS.md injection into standing orders");
        btnAgentsMd.addListener(SWT.Selection, e -> onAgentsMdToggle.accept(btnAgentsMd.getSelection()));

        EclipseUiUtil.newSeparator(this);
        // --- Skills toggle ---
        btnSkills = new Button(this, SWT.TOGGLE);
        btnSkills.setSelection(true);
        btnSkills.setImage(images.getImage(ISharedImages.IMG_OBJ_FOLDER));
        btnSkills.setText("0 skills");
        btnSkills.setToolTipText("Click to manage individual skills");
        btnSkills.addListener(SWT.Selection, e -> {
            if (skillsProvider != null) {
                btnSkills.setSelection(!btnSkills.getSelection());
                showSkillsMenu();
            } else {
                onSkillsToggle.accept(btnSkills.getSelection());
            }
        });

        EclipseUiUtil.newSeparator(this);

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

    public void update(int skillCount, String agentFileName, boolean agentMdActive,
            IProject project, 
            IResource selectedResource) {

        // --- Pin: show/hide the button with project name ---
        boolean hasProject = project != null;
        RowData pinRd = (RowData) btnPin.getLayoutData();
        if (pinRd.exclude == hasProject) { // visibility changed
            pinRd.exclude = !hasProject;
            btnPin.setVisible(hasProject);
        }
        if (hasProject) {
            btnPin.setText(project.getName()); // 📌 ProjectName
        }

        // --- AGENTS.md toggle: enable/disable based on file presence, update label ---
        if (agentFileName == null) {
            btnAgentsMd.setEnabled(false);
            btnAgentsMd.setSelection(false);
            btnAgentsMd.setText("No agents.md");
        } else {
            btnAgentsMd.setSelection(agentMdActive);
            btnAgentsMd.setEnabled(true);
            String name = agentFileName;
            if (!agentMdActive) name = agentFileName + " off";
            btnAgentsMd.setText(name);
        }

        // Keep selectedResource available for prompt context, but do not show the
        // selected/open file name in the status line.
        fileLabel.setText("");

        setSkillCount(skillCount);

        layout(true);
        getParent().layout(new Control[] { this });
    }

    /** Update the skills toggle button text with the loaded skill count. */
    public void setSkillCount(int count) {
        btnSkills.setText(count + " skill" + (count != 1 ? "s" : ""));
        btnSkills.setSelection(count > 0);
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

    /** Sync the AGENTS.md button state without firing the listener. */
    public void setAgentsMdEnabled(boolean enabled) {
        btnAgentsMd.setSelection(enabled);
    }

    /** Returns whether the AGENTS.md toggle is currently on. */
    public boolean isAgentsMdEnabled() {
        return btnAgentsMd.getSelection();
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

    /** Set the provider for loading skills and callback for menu changes. */
    public void setSkillsMenuHandler(Supplier<List<SkillPromptFile>> provider, Consumer<SkillMenuSelection> onChange) {
        this.skillsProvider = provider;
        this.onSkillMenuChange = onChange;
    }

    private void showSkillsMenu() {
        if (skillsProvider == null) return;

        List<SkillPromptFile> skills = skillsProvider.get();
        if (skills.isEmpty()) return;

        Menu menu = new Menu(btnSkills);

        // "All" option at the top
        MenuItem allItem = new MenuItem(menu, SWT.CHECK);
        allItem.setText("All Skills");
        boolean allEnabled = skills.stream().allMatch(SkillPromptFile::isEnabled);
        allItem.setSelection(allEnabled);
        allItem.addListener(SWT.Selection, e -> {
            boolean enable = allItem.getSelection();
            if (onSkillMenuChange != null) {
                onSkillMenuChange.accept(new SkillMenuSelection(null, enable, true));
            }
        });

        new MenuItem(menu, SWT.SEPARATOR);

        // Individual skill items
        for (SkillPromptFile skill : skills) {
            MenuItem item = new MenuItem(menu, SWT.CHECK);
            item.setText(skill.name());
            item.setSelection(skill.isEnabled());
            item.addListener(SWT.Selection, e -> {
                if (onSkillMenuChange != null) {
                    onSkillMenuChange.accept(new SkillMenuSelection(skill.name(), item.getSelection(), false));
                }
            });
        }

        menu.setVisible(true);
    }

    /** Encapsulates skill menu selection results. */
    public static class SkillMenuSelection {
        public final String skillName;      // null for "All"
        public final boolean enabled;       // new state
        public final boolean isAllSkills;   // true if "All" was selected

        public SkillMenuSelection(String skillName, boolean enabled, boolean isAllSkills) {
            this.skillName = skillName;
            this.enabled = enabled;
            this.isAllSkills = isAllSkills;
        }
    }
}
