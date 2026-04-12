package org.sterl.llmpeon.parts.shared;

import java.net.MalformedURLException;
import java.net.URI;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Widget;

public class ImageUtil {
    
    public static final String STOP = "platform:/plugin/org.eclipse.ui/icons/full/elcl16/stop.svg";
    public static final String MICROPHONE = "platform:/plugin/org.sterl.llmpeon/icons/microphone.svg";

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
