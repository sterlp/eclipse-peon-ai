package org.sterl.llmpeon.test;

import java.io.File;

import org.eclipse.core.runtime.FileLocator;
import org.osgi.framework.FrameworkUtil;

public final class PeonTestFixture {

    public static final String PROJECT_NAME = "test_project";
    public static final String PROPERTY = "peon.test.project";

    private static final int MAX_PARENT_LEVELS = 6;
    private static final String PROPERTY_HINT = " (set -D" + PROPERTY + "=…)";

    private PeonTestFixture() {
    }

    static File resolve(String propertyValue, File bundleDir) {
        if (propertyValue != null && !propertyValue.isBlank()) {
            return requireFixture(new File(propertyValue));
        }

        File current = bundleDir.getAbsoluteFile();
        for (int level = 0; level <= MAX_PARENT_LEVELS && current != null; level++) {
            File fixture = new File(current, PROJECT_NAME);
            if (isFixture(fixture)) return fixture.toPath().normalize().toFile();
            current = current.getParentFile();
        }
        throw missingFixture(new File(bundleDir, PROJECT_NAME));
    }

    public static File dir() {
        var bundle = FrameworkUtil.getBundle(PeonTestFixture.class);
        if (bundle == null) throw new IllegalStateException("test bundle location unavailable");
        var bundleDir = FileLocator.getBundleFileLocation(bundle)
                .orElseThrow(() -> new IllegalStateException("test bundle location unavailable"));
        return resolve(System.getProperty(PROPERTY), bundleDir);
    }

    public static File repoRoot() {
        return dir().getParentFile();
    }

    private static File requireFixture(File fixture) {
        File normalized = fixture.getAbsoluteFile().toPath().normalize().toFile();
        if (!isFixture(normalized)) throw missingFixture(normalized);
        return normalized;
    }

    private static boolean isFixture(File fixture) {
        return new File(fixture, ".project").isFile();
    }

    private static IllegalStateException missingFixture(File fixture) {
        File normalized = fixture.getAbsoluteFile().toPath().normalize().toFile();
        return new IllegalStateException("test fixture not found: " + normalized + PROPERTY_HINT);
    }
}
