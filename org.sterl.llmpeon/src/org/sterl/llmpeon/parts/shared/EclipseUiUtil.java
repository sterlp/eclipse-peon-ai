package org.sterl.llmpeon.parts.shared;

import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.ui.css.swt.theme.IThemeEngine;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.PlatformUI;

public class EclipseUiUtil {

    public static final String DARK_THEME_ID = "org.eclipse.e4.ui.css.theme.e4_dark";
    public static final String DARK_THEME_NAME = "dark";
    public static final String LIGHT_THEME_NAME = "light";
    public static final String CSS_CLASS_HEADER_BAR_WIDGET = "org-sterl-llmpeon-parts-widget-header-bar-widget";
    public static final String CSS_CLASS_USER_QUESTION_RESPONSE_WIDGET = "org-sterl-llmpeon-parts-widget-user-question-response-widget";
    public static final String CSS_CLASS_TEXT_INPUT_WIDGET = "org-sterl-llmpeon-parts-widget-text-input-widget";

    public static Label newSeparator(Composite parent) {
        var result = new Label(parent, SWT.SEPARATOR | SWT.VERTICAL);
        result.setLayoutData(new RowData(SWT.DEFAULT, 16));
        return result;
    }

    /**
     * Resolves the current Eclipse theme as {@code "dark"} or {@code "light"}.
     */
    public static String resolveTheme(IEclipseContext context) {
        if (PlatformUI.isWorkbenchRunning() && context != null) {
            IThemeEngine themeEngine = context.get(IThemeEngine.class);
            if (themeEngine != null && DARK_THEME_ID.equals(themeEngine.getActiveTheme().getId())) {
                return DARK_THEME_NAME;
            }
        }
        return LIGHT_THEME_NAME;
    }
}
