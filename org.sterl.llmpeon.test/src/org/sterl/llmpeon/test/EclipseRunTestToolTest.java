package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.jdt.junit.model.ITestCaseElement;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sterl.llmpeon.parts.tools.EclipseRunTestTool;
import org.sterl.llmpeon.parts.tools.PdeTestLaunchConfig;
import org.sterl.llmpeon.shared.CallStats;

public class EclipseRunTestToolTest {

    private static final Instant T0 = Instant.parse("2026-09-22T14:32:00Z");

    /** Fixed clock the test advances manually — own instance, no coupling to other tests. */
    static final class MutableClock extends Clock {
        private Instant instant = T0;
        void advance(Duration d) { instant = instant.plus(d); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    // UC-TD-3
    @Test
    public void formatResultsEndsWithStatsSuffix() {
        // GIVEN a report for a finished run with a fixed clock
        var stats = new CallStats(Clock.fixed(T0, ZoneOffset.UTC));

        // WHEN the results are formatted
        String report = EclipseRunTestTool.formatResults("All tests in X", 3, 0,
                new ArrayList<ITestCaseElement>(), 5, stats);

        // THEN the header shape is unchanged and the last line is the stats suffix
        assertTrue("header regression", report.startsWith("Test run: All tests in X"));
        assertTrue("header regression", report.contains("Tests:    3"));
        var lines = new ArrayList<String>();
        report.lines().forEach(lines::add);
        assertEquals("(0s, 14:32)", lines.get(lines.size() - 1));
    }

    // UC-TD-3
    @Test
    public void timeoutReportUsesMeasuredDuration() {
        // GIVEN a run that timed out after 175s of measured time
        var clock = new MutableClock();
        var stats = new CallStats(clock);
        clock.advance(Duration.ofSeconds(175));

        // WHEN the timeout report is built
        String report = EclipseRunTestTool.timeoutReport(10, 2, stats);

        // THEN it carries the measured duration, the counters and the stats suffix
        assertTrue("measured duration", report.startsWith("Test run timed out after 2m 55s."));
        assertTrue("counters", report.contains("10 tests ran, 2 failures so far."));
        var lines = new ArrayList<String>();
        report.lines().forEach(lines::add);
        assertEquals("(2m 55s, 14:34)", lines.get(lines.size() - 1));
    }

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
