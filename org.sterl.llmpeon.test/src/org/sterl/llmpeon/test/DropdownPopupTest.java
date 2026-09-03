package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.widget.dropdown.DropdownItem;
import org.sterl.llmpeon.parts.widget.dropdown.DropdownPopup;

/**
 * Workbench-display test for {@link DropdownPopup} (SWT-UI, reuses the PDE workbench's
 * {@link Display}; SWT supports only one display per process). Skipped (Assume) when no
 * workbench display is available.
 */
public class DropdownPopupTest extends AbstractUnitTest {

    private static final long SETTLE_MS = 100;

    private Display display;
    private Shell shell;
    private Composite anchor;

    @Before
    public void setUpDisplay() {
        try {
            ui(() -> {
                display = Display.getDefault();
                shell = new Shell(display);
                anchor = new Composite(shell, SWT.NONE);
                anchor.setSize(10, 10);
                return null;
            });
        } catch (AssertionError e) {
            Assume.assumeNoException("no workbench display available — SWT test skipped", e.getCause());
        }
        if (display == null || shell == null) {
            Assume.assumeNoException("no workbench display available — SWT test skipped",
                    new IllegalStateException("Display.getDefault() returned null"));
        }
    }

    @After
    public void tearDownDisplay() {
        if (shell != null && !shell.isDisposed()) {
            try {
                ui(() -> {
                    shell.dispose();
                    return null;
                });
            } catch (AssertionError ignored) {
                // display already gone — nothing to dispose
            }
        }
        shell = null;
        // never dispose the workbench's display
    }

    @Test
    public void selectsAndCloses() {
        // GIVEN an open popup with three items
        var popup = ui(() -> new DropdownPopup(shell, anchor));
        AtomicReference<String> selected = new AtomicReference<>();
        ui(() -> {
            popup.setSelectionListener(selected::set);
            open(popup);
            return null;
        });
        assertTrue("popup not open", ui(() -> popup.isOpen()));

        // WHEN the user clicks the second item
        ui(() -> {
            clickItem(findPopupShell(shell), 1);
            return null;
        });
        sleep(SETTLE_MS);

        // THEN the selection listener fires with that id and the popup closes
        assertEquals("agent-2", selected.get());
        assertFalse("popup still open", ui(() -> popup.isOpen()));
    }

    @Test
    public void closesOnDeactivate() {
        // GIVEN an open popup (no anchor, so Deactivate is not suppressed by the anchor-cursor guard)
        var popup = ui(() -> {
            var p = new DropdownPopup(shell, null);
            open(p);
            return p;
        });
        assertTrue("popup not open", ui(() -> popup.isOpen()));

        // WHEN the popup shell is deactivated
        ui(() -> {
            findPopupShell(shell).notifyListeners(SWT.Deactivate, null);
            return null;
        });

        // THEN the popup closes
        assertFalse("popup still open", ui(() -> popup.isOpen()));
    }

    // --- helpers ---

    /** Runs the given code on the workbench UI thread and returns its result (or throws). */
    private <T> T ui(Supplier<T> fn) {
        try {
            return EclipseUtil.runInUiThread(fn).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted waiting for UI thread", e);
        } catch (Exception e) {
            throw new AssertionError("UI thread call failed", e);
        }
    }

    /** UI-thread only. */
    private static void open(DropdownPopup popup) {
        popup.open(new Point(0, 0), List.of(
                DropdownItem.of("agent-1", "Agent 1"),
                DropdownItem.of("agent-2", "Agent 2"),
                DropdownItem.of("agent-3", "Agent 3")), "agent-1");
    }

    /** UI-thread only. The popup shell is a top-level child of the parent shell. */
    private static Shell findPopupShell(Shell parent) {
        for (Control child : parent.getChildren()) {
            if (child instanceof Shell s && !s.isDisposed() && (s.getStyle() & SWT.ON_TOP) != 0) {
                return s;
            }
        }
        throw new AssertionError("no popup shell found in " + parent);
    }

    /** UI-thread only. Fires a mouse-down on the given item (0-based). */
    private static void clickItem(Shell popupShell, int index) {
        for (Control child : popupShell.getChildren()) {
            if (!(child instanceof ScrolledComposite scrolled)) {
                continue;
            }
            if (!(scrolled.getContent() instanceof Composite container)) {
                continue;
            }
            Control[] itemComps = container.getChildren();
            if (index >= itemComps.length) {
                throw new AssertionError("no item " + index + " (have " + itemComps.length + ")");
            }
            itemComps[index].notifyListeners(SWT.MouseDown, null);
            return;
        }
        throw new AssertionError("no scrolled composite in popup shell");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted", e);
        }
    }
}
