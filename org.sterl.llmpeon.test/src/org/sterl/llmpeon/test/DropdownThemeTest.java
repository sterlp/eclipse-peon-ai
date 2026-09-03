package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.sterl.llmpeon.parts.shared.EclipseUiUtil;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.widget.dropdown.DropdownTheme;

/**
 * Test for {@link DropdownTheme}: exact reference palette values (pure RGB) and the
 * per-(display, theme) Color cache. Needs the workbench display (Assume-skip without it).
 */
public class DropdownThemeTest extends AbstractUnitTest {

    private Display display;
    private Shell shell;

    @Before
    public void setUpDisplay() {
        try {
            ui(() -> {
                display = Display.getDefault();
                shell = new Shell(display);
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
    public void darkPaletteMatchesReference() {
        // GIVEN the dark palette for the workbench display
        var palette = ui(() -> DropdownTheme.palette(display, EclipseUiUtil.DARK_THEME_NAME));

        // THEN all colors carry the reference dark RGB values
        assertRgb(palette.popupBg(), DropdownTheme.DARK_POPUP_BG);
        assertRgb(palette.itemFocusBg(), DropdownTheme.DARK_ITEM_FOCUS_BG);
        assertRgb(palette.buttonFocusBg(), DropdownTheme.DARK_BUTTON_FOCUS_BG);
        assertRgb(palette.border(), DropdownTheme.DARK_BORDER);
        assertRgb(palette.focusBorder(), DropdownTheme.FOCUS_BORDER);
        assertRgb(palette.text(), DropdownTheme.DARK_TEXT);
    }

    @Test
    public void lightPaletteMatchesReference() {
        // GIVEN the light palette for the workbench display
        var palette = ui(() -> DropdownTheme.palette(display, EclipseUiUtil.LIGHT_THEME_NAME));

        // THEN all colors carry the reference light RGB values
        assertRgb(palette.popupBg(), DropdownTheme.LIGHT_POPUP_BG);
        assertRgb(palette.itemFocusBg(), DropdownTheme.LIGHT_ITEM_FOCUS_BG);
        assertRgb(palette.buttonFocusBg(), DropdownTheme.LIGHT_BUTTON_FOCUS_BG);
        assertRgb(palette.border(), DropdownTheme.LIGHT_BORDER);
        assertRgb(palette.focusBorder(), DropdownTheme.FOCUS_BORDER);
        assertRgb(palette.text(), DropdownTheme.LIGHT_TEXT);
    }

    @Test
    public void paletteIsCachedPerDisplayAndTheme() {
        // GIVEN the workbench display
        // WHEN the same (display, theme) is resolved twice and a different theme once
        var dark1 = ui(() -> DropdownTheme.palette(display, EclipseUiUtil.DARK_THEME_NAME));
        var dark2 = ui(() -> DropdownTheme.palette(display, EclipseUiUtil.DARK_THEME_NAME));
        var light = ui(() -> DropdownTheme.palette(display, EclipseUiUtil.LIGHT_THEME_NAME));

        // THEN the same instance is returned per theme, different instances across themes
        assertSame(dark1, dark2);
        assertNotSame(dark1, light);
    }

    @Test
    public void currentResolvesWorkbenchTheme() {
        // GIVEN the workbench display
        // WHEN current() is resolved
        var current = ui(() -> DropdownTheme.current(display));
        var expected = ui(() -> DropdownTheme.palette(display, EclipseUiUtil.resolveTheme()));

        // THEN it equals the palette of the resolved workbench theme
        assertSame(expected, current);
    }

    // --- helpers ---

    private static void assertRgb(Color color, RGB expected) {
        assertEquals("expected " + expected, expected, color.getRGB());
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
}
