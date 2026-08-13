package org.sterl.llmpeon.parts.shared;

import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.ui.css.swt.theme.IThemeEngine;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.themes.IThemeManager;

public class EclipseUiUtil {

    public interface ThemeChangeListener {
        void onThemeChange(String theme);
    }

    public static final String DARK_THEME_ID = "org.eclipse.e4.ui.css.theme.e4_dark";
    public static final String DARK_THEME_NAME = "dark";
    public static final String LIGHT_THEME_NAME = "light";
    public static final String CSS_CLASS_HEADER_BAR_WIDGET = "org-sterl-llmpeon-parts-widget-header-bar-widget";
    public static final String CSS_CLASS_USER_QUESTION_RESPONSE_WIDGET = "org-sterl-llmpeon-parts-widget-user-question-response-widget";
    public static final String CSS_CLASS_TEXT_INPUT_WIDGET = "org-sterl-llmpeon-parts-widget-text-input-widget";
    private static final String DARK_FOREGROUND_PROPERTY_NAME = "org.eclipse.ui.workbench.DARK_FOREGROUND";

    public static Label newSeparator(Composite parent) {
        var result = new Label(parent, SWT.SEPARATOR | SWT.VERTICAL);
        result.setLayoutData(new RowData(SWT.DEFAULT, 16));
        return result;
    }

    /**
     * Resolves the current Eclipse theme as {@code "dark"} or {@code "light"}.
     */
    public static String resolveTheme() {
        return PlatformUI.isWorkbenchRunning()
                ? resolveTheme(PlatformUI.getWorkbench().getService(IEclipseContext.class))
                : LIGHT_THEME_NAME;
    }

    private static String resolveTheme(IEclipseContext context) {
        if (context != null) {
            IThemeEngine themeEngine = context.get(IThemeEngine.class);
            if (themeEngine != null && DARK_THEME_ID.equals(themeEngine.getActiveTheme().getId())) {
                return DARK_THEME_NAME;
            }
        }
        return LIGHT_THEME_NAME;
    }


    public static void addThemeChangeListener(ThemeChangeListener listener) {
        IEclipseContext context = PlatformUI.getWorkbench().getService(IEclipseContext.class);
        var theme = resolveTheme(context);
        PlatformUI.getWorkbench().getThemeManager().addPropertyChangeListener(new IPropertyChangeListener() {
            String currentTheme = theme;

            @Override
            public void propertyChange(PropertyChangeEvent e) {
                if (e.getProperty().equals(DARK_FOREGROUND_PROPERTY_NAME)
                        || e.getProperty().equals(IThemeManager.CHANGE_CURRENT_THEME)) {
                    String newTheme = resolveTheme(context);
                    if (!currentTheme.equals(newTheme)) {
                        currentTheme = newTheme;
                        listener.onThemeChange(newTheme);
                    }
                }
            }
        });
    }
}
