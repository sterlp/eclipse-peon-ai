package org.sterl.llmpeon.parts.tools;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.jdt.junit.TestRunListener;
import org.eclipse.jdt.junit.model.ITestCaseElement;
import org.eclipse.jdt.junit.model.ITestElement.Result;
import org.eclipse.jdt.junit.model.ITestRunSession;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.shared.WaitUtil;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class EclipseRunTestTool extends AbstractEclipseTool {

    private static final Duration MAX_TEST_DURATION = Duration.ofMinutes(10);
    private static final long POLL_INTERVAL_MS = 500;

    @Tool("Eclipse: Run JUnit 5 tests.")
    public String runTests(
            @P("project name") String projectName,
            @P("optional fully qualified test class, empty = all tests") String testClassName) {

        var project = EclipseUtil.findOpenProject(projectName);
        if (project.isEmpty()) {
            throw new IllegalArgumentException("Project not found: " + projectName
                    + ". Use listAllOpenEclipseProjects to find the correct name.");
        }

        IJavaProject javaProject = JavaCore.create(project.get());
        if (javaProject == null || !javaProject.exists()) {
            throw new IllegalArgumentException(projectName + " is not a Java project.");
        }

        boolean runAll = testClassName == null || testClassName.isBlank();
        String launchName = runAll
                ? "Run all tests in " + projectName
                : "Run " + testClassName + " in " + projectName;

        onTool(launchName);
        try {
            // Collect failures directly in the listener
            var failures = Collections.synchronizedList(new ArrayList<String>());
            var testCount = new int[]{0};
            var finished = new AtomicBoolean(false);
            var sessionName = new String[]{launchName};

            TestRunListener listener = new TestRunListener() {
                @Override
                public void sessionStarted(ITestRunSession session) {
                    sessionName[0] = session.getTestRunName();
                }

                @Override
                public void testCaseFinished(ITestCaseElement testCase) {
                    testCount[0]++;
                    Result result = testCase.getTestResult(false);
                    if (result == Result.ERROR || result == Result.FAILURE) {
                        failures.add(formatFailure(testCase));
                    }
                }

                @Override
                public void sessionFinished(ITestRunSession session) {
                    finished.set(true);
                }
            };
            JUnitCore.addTestRunListener(listener);

            try {
                // Create and launch configuration
                var launchManager = DebugPlugin.getDefault().getLaunchManager();
                var type = launchManager.getLaunchConfigurationType(
                        "org.eclipse.jdt.junit.launchconfig");

                ILaunchConfigurationWorkingCopy wc = type.newInstance(null, launchName);
                wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME,
                        javaProject.getElementName());
                wc.setAttribute("org.eclipse.jdt.junit.TEST_KIND",
                        "org.eclipse.jdt.junit.loader.junit5");

                if (runAll) {
                    wc.setAttribute("org.eclipse.jdt.junit.CONTAINER",
                            javaProject.getHandleIdentifier());
                } else {
                    IType testType = javaProject.findType(testClassName);
                    if (testType == null || !testType.exists()) {
                        throw new IllegalArgumentException("Test class not found in project '"
                                + projectName + "': " + testClassName
                                + ". Use a file search tool to find the correct class name and project.");
                    }
                    wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME,
                            testClassName);
                }

                ILaunchConfiguration config = wc.doSave();
                config.launch(ILaunchManager.RUN_MODE, getProgressMonitor());

                boolean completed = WaitUtil.awaitCondition(
                        finished::get, MAX_TEST_DURATION, POLL_INTERVAL_MS);

                if (!completed) {
                    return "Test run timed out after " + MAX_TEST_DURATION.toMinutes()
                            + " minutes. " + testCount[0] + " tests ran, "
                            + failures.size() + " failures so far.";
                }

                onTool("Reading test results of " + projectName);
                return formatResults(sessionName[0], testCount[0], failures);
            } finally {
                JUnitCore.removeTestRunListener(listener);
                // clean up temp launch config
                try {
                    for (var c : DebugPlugin.getDefault().getLaunchManager().getLaunchConfigurations()) {
                        if (launchName.equals(c.getName())) {
                            c.delete();
                            break;
                        }
                    }
                } catch (Exception ignore) {}
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to run tests: " + e.getMessage(), e);
        }
    }

    // -- formatting helpers --

    private static String formatFailure(ITestCaseElement testCase) {
        var sb = new StringBuilder();
        sb.append("FAIL: ").append(testCase.getTestClassName())
          .append(".").append(testCase.getTestMethodName());

        var trace = testCase.getFailureTrace();
        if (trace != null) {
            if (trace.getExpected() != null && trace.getActual() != null) {
                sb.append("\n  Expected: ").append(trace.getExpected());
                sb.append("\n  Actual:   ").append(trace.getActual());
            }
            if (trace.getTrace() != null) {
                String[] lines = trace.getTrace().split("\n");
                int maxLines = Math.min(lines.length, 10);
                sb.append("\n  Trace:");
                for (int i = 0; i < maxLines; i++) {
                    sb.append("\n    ").append(lines[i]);
                }
                if (lines.length > maxLines) {
                    sb.append("\n    ... (").append(lines.length - maxLines)
                      .append(" more lines)");
                }
            }
        }
        return sb.toString();
    }

    private static String formatResults(String sessionName, int testCount, List<String> failures) {
        var sb = new StringBuilder();
        sb.append("Test run: ").append(sessionName).append("\n");
        sb.append("Tests: ").append(testCount).append("\n");

        if (failures.isEmpty()) {
            sb.append("Result: OK\nAll tests passed.");
        } else {
            sb.append("Result: FAILURE\n");
            sb.append("Failures: ").append(failures.size()).append("\n\n");
            for (String f : failures) {
                sb.append(f).append("\n");
            }
        }
        return sb.toString();
    }
}
