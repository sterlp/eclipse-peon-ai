package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sterl.llmpeon.parts.tools.PdeTestLaunchConfig;

public class EclipseRunTestToolTest {

    private String previousWorkspaceProperty;

    @Before
    public void rememberWorkspaceProperty() {
        previousWorkspaceProperty = System.getProperty(PdeTestLaunchConfig.WORKSPACE_PROPERTY);
    }

    @After
    public void restoreWorkspaceProperty() {
        if (previousWorkspaceProperty == null) {
            System.clearProperty(PdeTestLaunchConfig.WORKSPACE_PROPERTY);
        } else {
            System.setProperty(PdeTestLaunchConfig.WORKSPACE_PROPERTY, previousWorkspaceProperty);
        }
    }

    @Test
    public void launchConfigDisablesAskClear() throws Exception {
        ILaunchConfigurationWorkingCopy config = newConfig();

        PdeTestLaunchConfig.applyUnattended(config);

        assertFalse(config.getAttribute("askclear", true));
        assertFalse(config.getAttribute("clearws", true));
        assertEquals(PdeTestLaunchConfig.resolveWorkspaceLocation(),
                config.getAttribute("location", ""));
    }

    @Test
    public void usesStableWorkspaceLocation() {
        rememberAndSetWorkspaceProperty(null);
        String expected = ResourcesPlugin.getWorkspace().getRoot().getLocation()
                .append(".metadata").append("peon-test-ws").toOSString();

        assertEquals(expected, PdeTestLaunchConfig.resolveWorkspaceLocation());
    }

    @Test
    public void reusesWorkspaceAcrossRuns() throws Exception {
        rememberAndSetWorkspaceProperty(ResourcesPlugin.getWorkspace().getRoot().getLocation()
                .append("custom-peon-test-ws").toOSString());
        ILaunchConfigurationWorkingCopy config = newConfig();

        PdeTestLaunchConfig.applyUnattended(config);
        String firstLocation = config.getAttribute("location", "");
        PdeTestLaunchConfig.applyUnattended(config);

        assertEquals(firstLocation, config.getAttribute("location", ""));
    }

    private ILaunchConfigurationWorkingCopy newConfig() throws Exception {
        return DebugPlugin.getDefault().getLaunchManager()
                .getLaunchConfigurationType("org.eclipse.jdt.junit.launchconfig")
                .newInstance(null, "peon-test-cfg");
    }

    private void rememberAndSetWorkspaceProperty(String value) {
        if (value == null) {
            System.clearProperty(PdeTestLaunchConfig.WORKSPACE_PROPERTY);
        } else {
            System.setProperty(PdeTestLaunchConfig.WORKSPACE_PROPERTY, value);
        }
    }
}
