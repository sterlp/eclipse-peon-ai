package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
import org.sterl.llmpeon.parts.widget.dropdown.DropdownButton;
import org.sterl.llmpeon.parts.widget.dropdown.DropdownItem;

/**
 * Workbench-display test for {@link DropdownButton} (SWT-UI, reuses the PDE workbench's
 * {@link Display}; SWT supports only one display per process). Skipped (Assume) when no
 * workbench display is available.
 */
public class DropdownButtonTest extends AbstractUnitTest {

    private static final long SETTLE_MS = 100;

    private Display display;
    private Shell shell;
    private Composite parent;

    @Before
    public void setUpDisplay() {
        try {
            ui(() -> {
                display = Display.getDefault();
                shell = new Shell(display);
                parent = new Composite(shell, SWT.NONE);
                parent.setSize(300, 60);
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
    public void selectionListenerFires() {
        // GIVEN a dropdown button with three items
        var button = createButton(List.of(
                DropdownItem.of("agent-1", "Agent 1"),
                DropdownItem.of("agent-2", "Agent 2"),
                DropdownItem.of("agent-3", "Agent 3")), "agent-1");
        AtomicReference<String> selected = new AtomicReference<>();
        ui(() -> {
            button.setSelectionListener(selected::set);
            return null;
        });

        // WHEN the user opens the popup (click) and selects the second item
        ui(() -> {
            button.notifyListeners(SWT.MouseDown, null);
            return null;
        });
        sleep(SETTLE_MS);
        Shell popupShell = ui(() -> findPopupShell(shell));
        assertNotNull("popup not open", popupShell);
        ui(() -> {
            clickItem(popupShell, 1);
            return null;
        });
        sleep(SETTLE_MS);

        // THEN the selection listener fires with that id and the popup closes
        assertEquals("agent-2", selected.get());
        assertNull("popup still open", ui(() -> findPopupShell(shell)));
    }

    @Test
    public void getSelectedItemIdReturnsSetId() {
        // GIVEN a button with a selected item
        var button = createButton(List.of(
                DropdownItem.of("a", "A"),
                DropdownItem.of("b", "B")), "b");

        // THEN getSelectedItemId returns the id
        assertEquals("b", ui(() -> button.getSelectedItemId()));
    }

    @Test
    public void computeSizeIsPositive() {
        // GIVEN a button with a selected item
        var button = createButton(List.of(DropdownItem.of("a", "Agent Alpha")), "a");

        // WHEN computeSize is asked for the natural size
        Point size = ui(() -> button.computeSize(SWT.DEFAULT, SWT.DEFAULT));

        // THEN width and height are positive
        assertTrue("width <= 0", size.x > 0);
        assertTrue("height <= 0", size.y > 0);
    }

    @Test
    public void disabledButtonDoesNotOpenPopup() {
        // GIVEN a disabled dropdown button
        var button = createButton(List.of(DropdownItem.of("a", "A")), null);
        ui(() -> {
            button.setEnabled(false);
            return null;
        });

        // WHEN the user clicks the disabled button
        ui(() -> {
            button.notifyListeners(SWT.MouseDown, null);
            return null;
        });
        sleep(SETTLE_MS);

        // THEN the popup does not open
        assertNull("popup opened on disabled button", ui(() -> findPopupShell(shell)));
    }

    @Test
    public void disposesPopupOnDispose() {
        // GIVEN a dropdown button with an open popup
        var button = createButton(List.of(
                DropdownItem.of("a", "A"),
                DropdownItem.of("b", "B")), "a");
        ui(() -> {
            button.notifyListeners(SWT.MouseDown, null);
            return null;
        });
        sleep(SETTLE_MS);
        assertNotNull("popup not open", ui(() -> findPopupShell(shell)));

        // WHEN the button is disposed
        ui(() -> {
            button.dispose();
            return null;
        });

        // THEN no exception and the popup shell is gone
        assertNull("popup still open after dispose", ui(() -> findPopupShell(shell)));
    }

    // --- helpers ---

    /** UI-thread only. Creates a laid-out button in {@link #parent}. */
    private DropdownButton createButton(List<DropdownItem> items, String selectedId) {
        return ui(() -> {
            var b = new DropdownButton(parent, SWT.NONE);
            b.setItems(items);
            if (selectedId != null) {
                b.setSelectedItemId(selectedId);
            }
            b.pack();
            parent.layout(true, true);
            return b;
        });
    }

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

    /** UI-thread only. The popup shell is a top-level child of the parent shell. */
    private static Shell findPopupShell(Shell parent) {
        for (Control child : parent.getChildren()) {
            if (child instanceof Shell s && !s.isDisposed() && (s.getStyle() & SWT.ON_TOP) != 0) {
                return s;
            }
        }
        return null;
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
