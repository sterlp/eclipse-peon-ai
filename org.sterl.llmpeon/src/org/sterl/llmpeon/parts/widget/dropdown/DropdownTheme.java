// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package org.sterl.llmpeon.parts.widget.dropdown;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;
import org.sterl.llmpeon.parts.shared.EclipseUiUtil;

/**
 * Theme-aware colors for the custom dropdown widgets.
 *
 * <p>Default palette mirrors the reference look (copilot-for-eclipse): dark
 * popup {@code #1E1F22} with focus {@code #184785}, light popup {@code #FFFFFF} with focus
 * {@code #D4E2FF}. SWT {@link Color}s are cached per (display, theme); the workbench display
 * outlives the process, so the cached colors are never disposed separately (same as the
 * reference — one palette per theme per display).</p>
 */
public final class DropdownTheme {

    public static final RGB DARK_POPUP_BG = new RGB(30, 31, 34);
    public static final RGB DARK_ITEM_FOCUS_BG = new RGB(24, 71, 133);
    public static final RGB DARK_BUTTON_FOCUS_BG = new RGB(64, 64, 64);
    public static final RGB DARK_BORDER = new RGB(68, 68, 68);
    public static final RGB DARK_TEXT = new RGB(211, 210, 210);

    public static final RGB LIGHT_POPUP_BG = new RGB(255, 255, 255);
    public static final RGB LIGHT_ITEM_FOCUS_BG = new RGB(212, 226, 255);
    public static final RGB LIGHT_BUTTON_FOCUS_BG = new RGB(232, 232, 232);
    public static final RGB LIGHT_BORDER = new RGB(216, 216, 216);
    public static final RGB LIGHT_TEXT = new RGB(0, 0, 0);

    /** Keyboard focus frame color (identical for both themes). */
    public static final RGB FOCUS_BORDER = new RGB(55, 134, 246);

    private static final Map<Display, Map<String, Palette>> CACHE = new ConcurrentHashMap<>();

    private DropdownTheme() {
    }

    /**
     * Theme-aware palette for the current workbench theme.
     *
     * @param display the display the colors are created on (UI thread)
     * @return the palette for the active theme (same instance while theme + display are unchanged)
     */
    public static Palette current(Display display) {
        return palette(display, EclipseUiUtil.resolveTheme());
    }

    /**
     * Palette for the given theme name ({@link EclipseUiUtil#DARK_THEME_NAME} /
     * {@link EclipseUiUtil#LIGHT_THEME_NAME}).
     *
     * @param display the display the colors are created on (UI thread)
     * @param theme   "dark" or "light"
     * @return the cached palette for (display, theme)
     */
    public static Palette palette(Display display, String theme) {
        var perDisplay = CACHE.computeIfAbsent(display, d -> new ConcurrentHashMap<String, Palette>());
        return perDisplay.computeIfAbsent(theme, t -> {
            var dark = EclipseUiUtil.DARK_THEME_NAME.equals(t);
            return new Palette(
                    new Color(display, dark ? DARK_POPUP_BG : LIGHT_POPUP_BG),
                    new Color(display, dark ? DARK_ITEM_FOCUS_BG : LIGHT_ITEM_FOCUS_BG),
                    new Color(display, dark ? DARK_BUTTON_FOCUS_BG : LIGHT_BUTTON_FOCUS_BG),
                    new Color(display, dark ? DARK_BORDER : LIGHT_BORDER),
                    new Color(display, FOCUS_BORDER),
                    new Color(display, dark ? DARK_TEXT : LIGHT_TEXT));
        });
    }

    /**
     * Theme-aware SWT colors.
     *
     * @param popupBg        popup shell background
     * @param itemFocusBg    highlighted (hover/focus) popup row background
     * @param buttonFocusBg  dropdown button hover background
     * @param border         popup border color
     * @param focusBorder    keyboard focus frame color
     * @param text           popup text color
     */
    public record Palette(Color popupBg, Color itemFocusBg, Color buttonFocusBg, Color border,
            Color focusBorder, Color text) {}
}
