package org.sterl.llmpeon.test;

import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;

/**
 * R-WN fixture (org.sterl.llmpeon/docs/workspace-nesting.md): a 3-level nested project chain
 * {@code wna_root/wnb_mid/wnc_leaf} in a temp directory outside the workspace. The marker
 * file is written to disk BEFORE the projects exist, so one Disk path is visible under
 * three workspace paths:
 *
 * <ul>
 * <li>{@code /wna_root/wnb_mid/wnc_leaf/WnNested.java} (root view)</li>
 * <li>{@code /wnb_mid/wnc_leaf/WnNested.java} (mid view)</li>
 * <li>{@code /wnc_leaf/WnNested.java} (leaf view — the deepest project)</li>
 * </ul>
 *
 * Project names are alphabetical root &lt; mid &lt; leaf. Idempotent: existing projects with
 * the same names are deleted before creation.
 */
public class NestedProjectFixture {

    public static final String ROOT_NAME = "wna_root";
    public static final String MID_NAME = "wnb_mid";
    public static final String LEAF_NAME = "wnc_leaf";
    public static final String MARKER_FILE = "WnNested.java";
    public static final String MARKER = "wnGrepMarker";

    private static final String MARKER_CONTENT = String.join(System.lineSeparator(),
            "public class WnNested {",
            "    // " + MARKER + " one",
            "    // " + MARKER + " two",
            "    // " + MARKER + " three",
            "}");

    private final Path tempRoot;
    private IProject root;
    private IProject mid;
    private IProject leaf;
    private boolean created;

    public NestedProjectFixture() {
        try {
            tempRoot = Files.createTempDirectory("wn-nested-");
        } catch (IOException e) {
            throw new IllegalStateException("cannot create temp dir for nested fixture", e);
        }
    }

    /** Creates the chain root→mid→leaf (marker file on disk first), then refreshes the root. */
    public void create() {
        assumeTrue("Eclipse workspace not available", workspaceAvailable());
        var rootDir = tempRoot.resolve(ROOT_NAME);
        var midDir = rootDir.resolve(MID_NAME);
        var leafDir = midDir.resolve(LEAF_NAME);
        try {
            Files.createDirectories(leafDir);
            Files.write(leafDir.resolve(MARKER_FILE), MARKER_CONTENT.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("cannot write nested fixture marker file", e);
        }
        try {
            root = createProject(ROOT_NAME, rootDir);
            mid = createProject(MID_NAME, midDir);
            leaf = createProject(LEAF_NAME, leafDir);
            root.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
            created = true;
        } catch (CoreException e) {
            throw new IllegalStateException("cannot create nested fixture chain", e);
        }
    }

    /** Deletes the projects leaf→mid→root and the temp tree; safe to call when create failed. */
    public void destroy() {
        if (created) {
            created = false;
            for (IProject p : new IProject[] { leaf, mid, root }) {
                try {
                    if (p != null && p.exists()) p.delete(true, true, new NullProgressMonitor());
                } catch (CoreException e) {
                    throw new IllegalStateException("cannot delete nested fixture project " + p.getName(), e);
                }
            }
        }
        deleteTree(tempRoot);
    }

    public IProject root() {
        return root;
    }

    public IProject mid() {
        return mid;
    }

    public IProject leaf() {
        return leaf;
    }

    private IProject createProject(String name, Path location) throws CoreException {
        var workspace = ResourcesPlugin.getWorkspace();
        var project = workspace.getRoot().getProject(name);
        if (project.exists()) project.delete(true, true, new NullProgressMonitor());
        var description = workspace.newProjectDescription(name);
        description.setLocation(IPath.fromOSString(location.toString()));
        project.create(description, new NullProgressMonitor());
        project.open(new NullProgressMonitor());
        return project;
    }

    private static boolean workspaceAvailable() {
        try {
            ResourcesPlugin.getWorkspace().getRoot().getProjects();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private static void deleteTree(Path path) {
        if (path == null || !Files.exists(path)) return;
        try (var paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            throw new IllegalStateException("cannot delete nested fixture temp tree", e);
        }
    }
}
