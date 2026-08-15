package org.sterl.llmpeon.parts.widget;

import java.util.List;
import java.util.function.Supplier;

import org.eclipse.e4.ui.css.swt.CSSSWTConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.sterl.llmpeon.agent.NamedAgent;
import org.sterl.llmpeon.parts.PeonAiService.ToolStatus;
import org.sterl.llmpeon.parts.shared.EclipseUiUtil;
import org.sterl.llmpeon.parts.shared.ImageUtil;
import org.sterl.llmpeon.parts.shared.SwtUtil;

import dev.langchain4j.model.output.TokenUsage;

/**
 * The header strip above the chat: a cumulative {@link TokenHeaderWidget} (left) and a flat hammer
 * button that reveals the active-tools popup (right).
 * <p>
 * Painted on the native white background (mirrors the input widget's button column — see
 * {@code swt-integrated-input-buttons.md}) so it reads as one surface with the chat window, and the
 * hammer uses the same chrome-less {@link SwtUtil#createIconButton} as the mic/send buttons. Self
 * contained: it owns its layout, the tools-menu popup, and the token readout; the view only feeds it
 * usage via {@link #addTokenUsage(TokenUsage)} and supplies the data the menu needs.
 */
public class HeaderBarWidget extends Composite {

    private final Supplier<String> activeAgentName;
    private final Supplier<List<ToolStatus>> toolStatus;
    private final TokenHeaderWidget tokens;
    private final AiAgentStatusWidget roster;
    private volatile Menu toolsMenu; // disposed on next open / widget dispose to avoid a native resource leak

    public HeaderBarWidget(Composite parent, int style,
            Supplier<String> activeAgentName,
            Supplier<List<ToolStatus>> toolStatus,
            Supplier<List<NamedAgent>> statusAgents) {
        super(parent, style);
        this.activeAgentName = activeAgentName;
        this.toolStatus = toolStatus;
        GridLayout layout = new GridLayout(4, false);
        layout.marginHeight = 0;
        layout.marginWidth = 4;
        setLayout(layout);
        setBackgroundMode(SWT.INHERIT_DEFAULT);
        setData(CSSSWTConstants.CSS_CLASS_NAME_KEY, EclipseUiUtil.CSS_CLASS_HEADER_BAR_WIDGET);

        tokens = new TokenHeaderWidget(this, SWT.NONE);
        // Sits far left, natural width — the roster grabs the middle so both stay readable.
        tokens.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        tokens.setData(CSSSWTConstants.CSS_CLASS_NAME_KEY, EclipseUiUtil.CSS_CLASS_HEADER_BAR_WIDGET);

        // A "·" divider so the cumulative token readout reads as separate from the agent roster.
        Label divider = new Label(this, SWT.NONE);
        divider.setText("·");
        divider.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
        divider.setData(CSSSWTConstants.CSS_CLASS_NAME_KEY, EclipseUiUtil.CSS_CLASS_HEADER_BAR_WIDGET);

        roster = new AiAgentStatusWidget(this, SWT.NONE, statusAgents);
        // FILL the middle column so the roster gets the remaining width between tokens and hammer.
        roster.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        roster.setData(CSSSWTConstants.CSS_CLASS_NAME_KEY, EclipseUiUtil.CSS_CLASS_HEADER_BAR_WIDGET);

        Button hammer = SwtUtil.createIconButton(this,
                ImageUtil.loadImage(this,
                        EclipseUiUtil.DARK_THEME_NAME.equals(EclipseUiUtil.resolveTheme()) ? ImageUtil.HAMMER_DARK
                                : ImageUtil.HAMMER),
                "Show which tools are active for the selected agent");
        hammer.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
        hammer.addListener(SWT.Selection, e -> showToolsMenu(hammer));
        hammer.setData(CSSSWTConstants.CSS_CLASS_NAME_KEY, EclipseUiUtil.CSS_CLASS_HEADER_BAR_WIDGET);

        addDisposeListener(e -> {
            if (toolsMenu != null && !toolsMenu.isDisposed()) toolsMenu.dispose();
        });
    }

    @Override
    public void dispose() {
        super.dispose();
        if (toolsMenu != null && !toolsMenu.isDisposed()) toolsMenu.dispose();
    }

    /** Accumulates one LLM response's real usage into the session readout. UI-thread only. */
    public void addTokenUsage(TokenUsage usage) {
        tokens.addUsage(usage);
    }

    /** Re-reads the agent roster (names, context sizes, active/working state). UI-thread only. */
    public void refreshRoster() {
        roster.refresh();
    }

    /** Popup listing every tool with a ✓ for active and greyed-out for inactive tools. */
    private void showToolsMenu(Control anchor) {
        if (toolsMenu != null && !toolsMenu.isDisposed()) toolsMenu.dispose();
        toolsMenu = new Menu(anchor);

        MenuItem header = new MenuItem(toolsMenu, SWT.PUSH);
        new MenuItem(toolsMenu, SWT.SEPARATOR);

        var activeCount = 0;
        var tools = toolStatus.get();
        for (var t : tools) {
            MenuItem mi = new MenuItem(toolsMenu, SWT.PUSH);
            mi.setText((t.active() ? "✓  " : "–  ") + t.name() + (t.mcp() ? "  (MCP)" : ""));
            mi.setEnabled(t.active()); // inactive tools appear greyed out
            if (t.active()) ++activeCount;
        }
        header.setText("Tools for: " + activeAgentName.get()
            + " (" + activeCount + "/" + tools.size() + ")");

        toolsMenu.setLocation(anchor.toDisplay(0, anchor.getSize().y));
        toolsMenu.setVisible(true);
    }
}
