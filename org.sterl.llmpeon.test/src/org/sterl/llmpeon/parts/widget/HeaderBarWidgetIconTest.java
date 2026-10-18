package org.sterl.llmpeon.parts.widget;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.sterl.llmpeon.parts.ai.CodegraphConnectionService.Kind;
import org.sterl.llmpeon.parts.shared.ImageUtil;

/**
 * R-CG-8: the pure (state kind, dark theme) → icon-path mapping on {@link HeaderBarWidget} —
 * ON/OFF own color variants (setEnabled(false) does not dim images), ERROR the platform STOP
 * icon. Package-private test in the widget package (OSGi test fragment) to reach the
 * package-private {@link HeaderBarWidget#codegraphIcon} directly.
 */
public class HeaderBarWidgetIconTest {

    // R-CG-8
    @Test
    public void codegraphIconResolvesPerStateAndTheme() {
        // GIVEN the (state kind, dark theme) matrix WHEN the icon is resolved
        // THEN ON/OFF map to their own color variants, ERROR stays the platform STOP icon
        assertEquals(ImageUtil.CODEGRAPH, HeaderBarWidget.codegraphIcon(Kind.CONNECTED, false));
        assertEquals(ImageUtil.CODEGRAPH_DARK, HeaderBarWidget.codegraphIcon(Kind.CONNECTED, true));
        assertEquals(ImageUtil.CODEGRAPH_OFF, HeaderBarWidget.codegraphIcon(Kind.OFF, false));
        assertEquals(ImageUtil.CODEGRAPH_OFF_DARK, HeaderBarWidget.codegraphIcon(Kind.OFF, true));
        assertEquals(ImageUtil.STOP, HeaderBarWidget.codegraphIcon(Kind.ERROR, false));
        assertEquals(ImageUtil.STOP, HeaderBarWidget.codegraphIcon(Kind.ERROR, true));
    }
}
