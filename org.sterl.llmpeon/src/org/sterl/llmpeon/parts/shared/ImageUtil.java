package org.sterl.llmpeon.parts.shared;

import java.net.MalformedURLException;
import java.net.URI;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Widget;

public class ImageUtil {
    private static final String PATH = "platform:/plugin/org.sterl.llmpeon/icons/";

    public static final String FILE =     "platform:/plugin/org.eclipse.ui.editors/icons/full/obj16/file_obj.svg";
    public static final String FILE_NAV = "platform:/plugin/org.eclipse.ui/icons/full/eview16/filenav_nav.svg";
    public static final String STOP =     "platform:/plugin/org.eclipse.ui/icons/full/elcl16/stop.svg";
    public static final String MICROPHONE = "platform:/plugin/org.sterl.llmpeon/icons/microphone.svg";
    public static final String HAMMER = "platform:/plugin/org.sterl.llmpeon/icons/hammer.svg";
    public static final String HAMMER_DARK = "platform:/plugin/org.sterl.llmpeon/icons/hammer_dark.svg";
    public static final String CODEGRAPH = "platform:/plugin/org.sterl.llmpeon/icons/codegraph.svg";
    public static final String CODEGRAPH_DARK = "platform:/plugin/org.sterl.llmpeon/icons/codegraph_dark.svg";
    public static final String CODEGRAPH_OFF = "platform:/plugin/org.sterl.llmpeon/icons/codegraph_off.svg";
    public static final String CODEGRAPH_OFF_DARK = "platform:/plugin/org.sterl.llmpeon/icons/codegraph_off_dark.svg";
    public static final String CLEAR = "platform:/plugin/org.sterl.llmpeon/icons/clear.svg";
    public static final String THINK = "platform:/plugin/org.sterl.llmpeon/icons/think.svg";

    private static final String COMPACT_PATH =      "platform:/plugin/org.sterl.llmpeon/icons/compact.svg";
    private static final String COMPACT_DARK_PATH = "platform:/plugin/org.sterl.llmpeon/icons/compact_dark.svg";

    public static final String PIN = "platform:/plugin/org.eclipse.ui.console/icons/full/elcl16/pin.svg";
    
    public enum Icon {
        COMPACT(COMPACT_PATH, COMPACT_DARK_PATH),
        REFRESH(PATH + "refresh.svg", PATH + "refresh_dark.svg")
        ;
        
        
        private Icon(String light, String dark) {
            this.light = light;
            this.dark = dark;
        }
        private final String light;
        private final String dark;
        public String getLight() {
            return light;
        }
        public String getDark() {
            return dark;
        }
    }
    
    public static Image loadImage(Widget forWidget, Icon icon) {
        try {
            var v = EclipseUiUtil.isDarkTheme() ? icon.dark : icon.light;
            var result = ImageDescriptor
                    .createFromURL(URI.create(v).toURL())
                    .createImage();
            forWidget.addDisposeListener(e -> result.dispose());
            return result;
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public static Image loadImage(Widget forWidget, String path) {
        try {
            var result = ImageDescriptor
                    .createFromURL(URI.create(path).toURL())
                    .createImage();
            forWidget.addDisposeListener(e -> result.dispose());
            return result;
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}
