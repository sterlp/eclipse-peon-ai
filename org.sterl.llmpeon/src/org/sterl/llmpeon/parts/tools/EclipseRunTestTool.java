package org.sterl.llmpeon.parts.tools;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.jdt.junit.TestRunListener;
import org.eclipse.jdt.junit.launcher.JUnitLaunchShortcut;
import org.eclipse.jdt.junit.model.ITestCaseElement;
import org.eclipse.jdt.junit.model.ITestElement.Result;
import org.eclipse.jdt.junit.model.ITestRunSession;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.shared.WaitUtil;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * Runs JUnit tests via Eclipse's own launch infrastructure.
 *
 * <p>Uses {@link JUnitLaunchShortcut#createLaunchConfiguration(IJavaElement)} to create launch
 * configurations — this ensures all required attributes (test kind, runner, VM args) are populated
 * correctly by Eclipse's own logic rather than manual attribute setting.</p>
 *
 * <p>For Eclipse plugin projects (PDE nature), the shortcut is configured to use the PDE JUnit
 * launch type ({@code org.eclipse.pde.ui.JunitLaunchConfig}), which starts the OSGi framework and
 * resolves bundles before running tests. Standard Java projects use the JDT type
 * ({@code org.eclipse.jdt.junit.launchconfig}).</p>
 *
 * <p>The tool reuses existing launch configurations when possible, falling back to temporary
 * configs that are cleaned up after the test run.</p>
 */
public class EclipseRunTestTool extends AbstractEclipseTool {

    private static final Duration MAX_TEST_DURATION = Duration.ofMinutes(5);
    private static final long POLL_INTERVAL_MS = 800;

    private static final String PDE_JUNIT_LAUNCH_TYPE = "org.eclipse.pde.ui.JunitLaunchConfig";

    @Tool("Run JUnit tests (auto-detects JUnit 3/4/5/6). For Eclipse plugin projects, usePluginTest=true starts the OSGi framework.")
    public String eclipseRunTests(
            @P(name = "projectName") String projectName,
            @P(description = "fully qualified test class, empty = all tests in project", required = false, name = "testClassName")
            String testClassName,
            @P(description = "how many errors to return - default 5 to save tokens", name = "errorCount", required = false)
            Integer errorCount,
            @P(description = "force Plug-in Test mode (starts OSGi framework); auto-detected from PDE nature if omitted", name = "usePluginTest", required = false)
            Boolean usePluginTest) {

        ArgsUtil.requireNonBlank(projectName, "projectName");
        if (errorCount == null) errorCount = 5;

        var project = EclipseUtil.findOpenProject(projectName);
        if (project.isEmpty()) {
            throw new IllegalArgumentException("Project not found: " + projectName
                    + ". Known: " + EclipseUtil.openProjectsNames());
        }

        IJavaProject javaProject = JavaCore.create(project.get());
        if (javaProject == null || !javaProject.exists()) {
            throw new IllegalArgumentException(projectName + " is not a Java project.");
        }

        boolean runAll = testClassName == null || testClassName.isBlank();
        boolean pluginTest = isPluginTest(usePluginTest, project);

        IType testType = null;
        if (!runAll) {
            try {
                testType = javaProject.findType(testClassName);
                if (testType == null || !testType.exists()) {
                    throw new IllegalArgumentException("Test class not found in project '"
                            + projectName + "': " + testClassName
                            + ". Use a file search tool to find the correct class name and project.");
                }
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Failed to find test class '" + testClassName + "' in project '" + projectName + "': " + e.getMessage(), e);
            }
        }
        IJavaElement launchElement = runAll ? javaProject : testType;

        String launchType = pluginTest ? "Plug-in Test" : "JUnit";
        String description = runAll
                ? "Run all " + launchType + " tests in " + projectName
                : "Run " + launchType + " " + testClassName + " in " + projectName;

        onTool(description);

        var launchManager = DebugPlugin.getDefault().getLaunchManager();
        ILaunchConfiguration config;

        try {
            // Create shortcut first — it knows the correct type ID (JDT default or PDE override)
            ConfigurableJUnitShortcut shortcut = new ConfigurableJUnitShortcut(
                    pluginTest ? PDE_JUNIT_LAUNCH_TYPE : null);
            String configTypeId = shortcut.typeId();

            ILaunchConfigurationType type = launchManager.getLaunchConfigurationType(configTypeId);
            if (type == null) {
                throw new IllegalStateException("Launch configuration type not found: " + configTypeId
                        + ". Required PDE/JDT JUnit tooling may not be installed.");
            }

            // 1) Try to reuse an existing launch config matching this project/class
            ILaunchConfiguration existing = findExistingConfig(launchManager, type, javaProject, testType, runAll);

            if (existing != null) {
                if (pluginTest) {
                    ILaunchConfigurationWorkingCopy wc = existing.getWorkingCopy();
                    PdeTestLaunchConfig.applyUnattended(wc);
                    config = wc.doSave();
                } else {
                    config = existing;
                }
                onTool(description + " (reusing existing launch config)");
            } else {
                // 2) Fall back to Eclipse's own shortcut logic so all required
                //    attributes (incl. PDE bundles/application) are populated correctly
                ILaunchConfigurationWorkingCopy wc = shortcut.createConfig(launchElement);
                String namePrefix = runAll ? javaProject.getElementName() : testType.getFullyQualifiedName();
                String uniqueName = launchManager.generateLaunchConfigurationName(namePrefix);
                wc.rename(uniqueName);
                if (pluginTest) {
                    PdeTestLaunchConfig.applyUnattended(wc);
                }
                config = wc.doSave();
            }

            try {
                return runAndCollect(config, description, errorCount);
            } finally {
                // Don't delete — keep config so it can be reused on next run
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to run tests: " + e.getMessage(), e);
        }
    }

    private boolean isPluginTest(Boolean usePluginTest,
            Optional<IProject> project) {
        boolean hasPdeNature;
        try {
            hasPdeNature = project.get().isNatureEnabled("org.eclipse.pde.PluginNature");
        } catch (Exception e) {
            hasPdeNature = false;
        }
        boolean pluginTest = usePluginTest != null ? usePluginTest : hasPdeNature;
        return pluginTest;
    }

    private ILaunchConfiguration findExistingConfig(ILaunchManager launchManager, ILaunchConfigurationType type,
            IJavaProject javaProject, IType testType, boolean runAll) throws CoreException {
        for (ILaunchConfiguration c : launchManager.getLaunchConfigurations(type)) {
            String cProject = c.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, "");
            if (!javaProject.getElementName().equals(cProject)) continue;

            if (runAll) {
                String container = c.getAttribute("org.eclipse.jdt.junit.CONTAINER", "");
                if (javaProject.getHandleIdentifier().equals(container)) return c;
            } else {
                String mainType = c.getAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, "");
                if (testType.getFullyQualifiedName().equals(mainType)) return c;
            }
        }
        return null;
    }

    private String runAndCollect(ILaunchConfiguration config, String launchName, int errorCount) throws Exception {
        var failures = Collections.synchronizedList(new ArrayList<ITestCaseElement>());
        var testCount = new int[]{0};
        var finished = new AtomicBoolean(false);
        var sessionName = new String[]{launchName};
        var ourSession = new ITestRunSession[]{null};

        TestRunListener listener = new TestRunListener() {
            @Override
            public void sessionStarted(ITestRunSession session) {
                // Capture the first session — this is ours (launched just above).
                // If another session started before ours (user ran a test first),
                // it was already captured, so we skip it.
                if (ourSession[0] != null) return;
                if (!isOurSession(session, config)) return;
                ourSession[0] = session;
                sessionName[0] = session.getTestRunName();
            }

            @Override
            public void testCaseFinished(ITestCaseElement testCase) {
                if (!isOurSession(testCase.getTestRunSession(), config)) return;
                testCount[0]++;
                Result result = testCase.getTestResult(false);
                if (result == Result.ERROR || result == Result.FAILURE) {
                    failures.add(testCase);
                }
            }

            @Override
            public void sessionFinished(ITestRunSession session) {
                if (!isOurSession(session, config)) return;
                finished.set(true);
            }
        };
        JUnitCore.addTestRunListener(listener);

        try {
            config.launch(ILaunchManager.RUN_MODE, getProgressMonitor());

            boolean completed = WaitUtil.awaitCondition(finished::get, MAX_TEST_DURATION, POLL_INTERVAL_MS);
            if (!completed) {
                return "Test run timed out after " + MAX_TEST_DURATION.toMinutes()
                        + " minutes. " + testCount[0] + " tests ran, "
                        + failures.size() + " failures so far.";
            }

            onTool("Reading test results of " + launchName);
            return formatResults(sessionName[0], testCount[0], failures, errorCount);
        } finally {
            JUnitCore.removeTestRunListener(listener);
        }
    }

    /**
     * Checks whether a session belongs to our launch configuration.
     * Filters out events from concurrent test runs (e.g. user manually running
     * tests while the LLM's run is in progress).
     *
     * ITestRunSession lacks getLaunch(), so we match by project only.
     * Name matching was dropped because JUnit 5 runners may decorate the display
     * name, causing mismatches with the config name. Project matching is reliable
     * — same-project concurrent runs are extremely unlikely in practice.
     */
    private static boolean isOurSession(ITestRunSession session, ILaunchConfiguration ourConfig) {
        try {
            var project = session.getLaunchedProject();
            if (project == null) return true; // can't determine, accept
            String cProject = ourConfig.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, "");
            return project.getElementName().equals(cProject);
        } catch (CoreException e) {
            // If we can't determine the session's identity, accept it —
            // better to count extra events than miss our own.
            return true;
        }
    }

    /**
     * Subclass of {@link JUnitLaunchShortcut} that allows specifying the launch configuration type
     * at runtime. Only overrides {@link JUnitLaunchShortcut#getLaunchConfigurationTypeId()} when
     * a PDE type ID is needed; otherwise the superclass default (JDT) kicks in.
     * This avoids hardcoding the JDT type ID, which tracks Eclipse releases automatically.
     */
    private static class ConfigurableJUnitShortcut extends JUnitLaunchShortcut {
        private final String overrideTypeId; // null = use JDT default from superclass

        ConfigurableJUnitShortcut(String overrideTypeId) {
            this.overrideTypeId = overrideTypeId;
        }

        @Override
        protected String getLaunchConfigurationTypeId() {
            return overrideTypeId != null ? overrideTypeId : super.getLaunchConfigurationTypeId();
        }

        /** Exposes the type ID for launch config type lookup before config creation. */
        String typeId() {
            return getLaunchConfigurationTypeId();
        }

        ILaunchConfigurationWorkingCopy createConfig(IJavaElement element) throws CoreException {
            return super.createLaunchConfiguration(element);
        }
    }

    // -- formatting helpers --

    private static String formatFailure(ITestCaseElement testCase) {
        var sb = new StringBuilder();
        sb.append("testClassName: ").append(testCase.getTestClassName())
          .append("\nmethod: ").append(testCase.getTestMethodName());

        var trace = testCase.getFailureTrace();
        if (trace != null) {
            if (trace.getExpected() != null) {
                sb.append("\n  Expected: ").append(trace.getExpected());
            }
            if (trace.getActual() != null) {
                sb.append("\n  Actual:   ").append(trace.getActual());
            }
            if (trace.getTrace() != null) {
                sb.append("\nTrace:\n").append(trace.getTrace());
            }
        }
        return sb.toString();
    }

    private static String formatResults(String sessionName, int testCount, List<ITestCaseElement> failures, int errorCount) {
        var sb = new StringBuilder();
        sb.append("Test run: ").append(sessionName).append("\n");
        sb.append("Tests:    ").append(testCount).append("\n");
        sb.append("Failures: ").append(failures.size()).append("\n");

        for (int i = 0; i < Math.min(errorCount, failures.size()); ++i) {
            sb.append(formatFailure(failures.get(i))).append("\n");
        }
        if (failures.size() > errorCount) {
            sb.append("Errors capped — fix the first " + errorCount + " problems, or run a specific test class");
        }
        return sb.toString();
    }
}
