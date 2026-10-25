package org.sterl.open.aifactory.shared;

import java.nio.file.Path;

/**
 * The second write root of the factory (R-WP-13): the system temp dir, resolved absolute and
 * normalized once so relative segments can never smuggle a path out of it.
 */
public final class TmpRoots {

    private TmpRoots() {
    }

    public static Path systemTmp() {
        return Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
    }
}
