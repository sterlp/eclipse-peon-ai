package org.sterl.llmpeon.parts.tools;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;

public final class PdeTestLaunchConfig {

    // Values of IPDELauncherConstants; literals avoid requiring the PDE launching bundle.
    static final String ATTR_LOCATION = "location";
    static final String ATTR_DOCLEAR = "clearws";
    static final String ATTR_ASKCLEAR = "askclear";

    public static final String WORKSPACE_PROPERTY = "peon.test.ws";

    private PdeTestLaunchConfig() {
    }

    public static String resolveWorkspaceLocation() {
        String configured = System.getProperty(WORKSPACE_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return ResourcesPlugin.getWorkspace().getRoot().getLocation()
                .append(".metadata").append("peon-test-ws").toOSString();
    }

    public static void applyUnattended(ILaunchConfigurationWorkingCopy config) {
        config.setAttribute(ATTR_LOCATION, resolveWorkspaceLocation());
        config.setAttribute(ATTR_DOCLEAR, false);
        config.setAttribute(ATTR_ASKCLEAR, false);
    }
}
