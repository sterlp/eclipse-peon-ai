package org.sterl.llmpeon.parts.widget;

import java.util.List;
import java.util.function.Supplier;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.sterl.llmpeon.parts.PeonAiService.ToolStatus;
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
    private Menu toolsMenu; // disposed on next open / widget dispose to avoid a native resource leak

    public HeaderBarWidget(Composite parent, int style,
            Supplier<String> activeAgentName,
            Supplier<List<ToolStatus>> toolStatus) {
        super(parent, style);
        this.activeAgentName = activeAgentName;
        this.toolStatus = toolStatus;

        Color bgWhite = getDisplay().getSystemColor(SWT.COLOR_WHITE);
        GridLayout layout = new GridLayout(2, false);
        layout.marginHeight = 0;
        layout.marginWidth = 4;
        setLayout(layout);
        setBackground(bgWhite);
        setBackgroundMode(SWT.INHERIT_DEFAULT);
        addPaintListener(e -> {
            e.gc.setBackground(bgWhite);
            e.gc.fillRectangle(getClientArea());
        });

        tokens = new TokenHeaderWidget(this, SWT.NONE);
        // FILL the left column so the readout always has width (its Label right-aligns inside).
        tokens.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Button hammer = SwtUtil.createIconButton(this,
                ImageUtil.loadImage(this, ImageUtil.HAMMER),
                "Show which tools are active for the selected agent");
        hammer.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
        hammer.addListener(SWT.Selection, e -> showToolsMenu(hammer));

        addDisposeListener(e -> {
            if (toolsMenu != null && !toolsMenu.isDisposed()) toolsMenu.dispose();
        });
    }

    /** Accumulates one LLM response's real usage into the session readout. UI-thread only. */
    public void addTokenUsage(TokenUsage usage) {
        tokens.addUsage(usage);
    }

    /** Popup listing every tool with a ✓ for active and greyed-out for inactive tools. */
    private void showToolsMenu(Control anchor) {
        // Dispose previous popup to avoid native resource leak.
        if (toolsMenu != null && !toolsMenu.isDisposed()) toolsMenu.dispose();

        Menu menu = new Menu(anchor);
        toolsMenu = menu;

        MenuItem header = new MenuItem(menu, SWT.PUSH);
        header.setText("Tools for: " + activeAgentName.get());
        header.setEnabled(false);
        new MenuItem(menu, SWT.SEPARATOR);

        for (var t : toolStatus.get()) {
            MenuItem mi = new MenuItem(menu, SWT.PUSH);
            mi.setText((t.active() ? "✓  " : "–  ") + t.name() + (t.mcp() ? "  (MCP)" : ""));
            mi.setEnabled(t.active()); // inactive tools appear greyed out
        }

        menu.setLocation(anchor.toDisplay(0, anchor.getSize().y));
        menu.setVisible(true);
    }
}
