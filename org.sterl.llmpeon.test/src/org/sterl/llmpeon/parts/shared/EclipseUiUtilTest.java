package org.sterl.llmpeon.parts.shared;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.eclipse.e4.core.contexts.EclipseContextFactory;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.ui.css.core.engine.CSSEngine;
import org.eclipse.e4.ui.css.core.util.resources.IResourceLocator;
import org.eclipse.e4.ui.css.swt.theme.ITheme;
import org.eclipse.e4.ui.css.swt.theme.IThemeEngine;
import org.junit.Test;
import org.w3c.dom.css.CSSStyleDeclaration;

/**
 * Headless regression tests for {@link EclipseUiUtil#resolveTheme()}: the active E4 theme is read
 * from the CSS engine and resolved to "dark"/"light". The engine's {@code getActiveTheme()} is
 * nullable — a missing context, theme engine, or active theme must all resolve to "light", never
 * throw (Night-Cycle 4 Inc1, 2026-10-15).
 */
@SuppressWarnings("restriction")
public class EclipseUiUtilTest {

    @Test
    public void themeNullResolvesToLight() {
        // Regression: getActiveTheme() == null used to throw NPE (EclipseUiUtil.resolveTheme)
        assertEquals(EclipseUiUtil.LIGHT_THEME_NAME, EclipseUiUtil.resolveTheme(contextWithActiveTheme(null)));
    }

    @Test
    public void darkActiveThemeResolvesToDark() {
        assertEquals(EclipseUiUtil.DARK_THEME_NAME,
                EclipseUiUtil.resolveTheme(contextWithActiveTheme(EclipseUiUtil.DARK_THEME_ID)));
    }

    @Test
    public void defaultActiveThemeResolvesToLight() {
        assertEquals(EclipseUiUtil.LIGHT_THEME_NAME,
                EclipseUiUtil.resolveTheme(contextWithActiveTheme(IThemeEngine.DEFAULT_THEME_ID)));
    }

    @Test
    public void emptyContextWithoutEngineResolvesToLight() {
        assertEquals(EclipseUiUtil.LIGHT_THEME_NAME, EclipseUiUtil.resolveTheme(EclipseContextFactory.create()));
    }

    @Test
    public void nullContextResolvesToLight() {
        assertEquals(EclipseUiUtil.LIGHT_THEME_NAME, EclipseUiUtil.resolveTheme(null));
    }

    /** A fresh headless E4 context carrying a fake engine whose active theme is the given id (or none). */
    private static IEclipseContext contextWithActiveTheme(String activeThemeId) {
        var context = EclipseContextFactory.create();
        ITheme activeTheme = activeThemeId == null ? null : new ITheme() {
            @Override public String getId() { return activeThemeId; }
            @Override public String getLabel() { return activeThemeId; }
        };
        context.set(IThemeEngine.class, new FakeThemeEngine(activeTheme));
        return context;
    }

    /** Minimal IThemeEngine: only {@link #getActiveTheme()} is meaningful; every other operation is unsupported. */
    private static final class FakeThemeEngine implements IThemeEngine {
        private final ITheme activeTheme;

        private FakeThemeEngine(ITheme activeTheme) {
            this.activeTheme = activeTheme;
        }

        @Override public ITheme getActiveTheme() { return activeTheme; }

        @Override public ITheme registerTheme(String id, String label, String basestylesheetURI) {
            throw new UnsupportedOperationException();
        }

        @Override public void registerStylesheet(String uri, String... themes) {
            throw new UnsupportedOperationException();
        }

        @Override public void registerResourceLocator(IResourceLocator locator, String... themes) {
            throw new UnsupportedOperationException();
        }

        @Override public List<ITheme> getThemes() {
            throw new UnsupportedOperationException();
        }

        @Override public void setTheme(String themeId, boolean restore) {
            throw new UnsupportedOperationException();
        }

        @Override public void setTheme(ITheme theme, boolean restore) {
            throw new UnsupportedOperationException();
        }

        @Override public void applyStyles(Object widget, boolean applyStylesToChildNodes) {
            throw new UnsupportedOperationException();
        }

        @Override public CSSStyleDeclaration getStyle(Object widget) {
            throw new UnsupportedOperationException();
        }

        @Override public void restore(String alternate) {
            throw new UnsupportedOperationException();
        }

        @Override public void addCSSEngine(CSSEngine cssEngine) {
            throw new UnsupportedOperationException();
        }

        @Override public void removeCSSEngine(CSSEngine cssEngine) {
            throw new UnsupportedOperationException();
        }
    }
}
