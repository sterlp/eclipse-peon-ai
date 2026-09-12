package org.sterl.llmpeon.parts.widget;

/**
 * SWT data key under which a control's CSS class name is stored so the E4 CSS engine can style it.
 * Mirrors the value of the (restricted) {@code org.eclipse.e4.ui.css.swt.CSSSWTConstants.CSS_CLASS_NAME_KEY}
 * (verified via {@code javap -constants} against the target's {@code org.eclipse.e4.ui.css.swt_0.17.100})
 * without touching the internal API.
 */
final class WidgetCss {

    static final String CSS_CLASS_NAME_KEY = "org.eclipse.e4.ui.css.CssClassName";

    private WidgetCss() {
    }
}
