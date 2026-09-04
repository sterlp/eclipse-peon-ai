package org.sterl.llmpeon.parts.shared;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.jspecify.annotations.Nullable;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;
import org.jspecify.annotations.NonNull;
import org.sterl.llmpeon.shared.AiMonitor.AiFileUpdate;
import org.sterl.llmpeon.shared.FileUtils;
import org.sterl.llmpeon.shared.StringUtil;

import jakarta.annotation.Nonnull;

public class EclipseUtil {
    // TODO move to EclipseUiUtil
    public static void runInUiThread(Composite parent, Runnable fn) {
        if (parent == null || parent.isDisposed())
            return;
        if (Display.getCurrent() == parent.getDisplay()) {
            fn.run();
            return;
        }
        parent.getDisplay().asyncExec(() -> {
            if (parent.isDisposed())
                return;
            fn.run();
        });
    }
    
    public static <T> CompletableFuture<T> runInUiThread(Supplier<T> fn) {
        final var result = new CompletableFuture<T>();
        var display = PlatformUI.getWorkbench().getDisplay();
        if (Display.getCurrent() == display) {
            complete(result, fn);
        } else {
            display.asyncExec(() -> complete(result, fn));
        }
        return result;
    }

    private static <T> void complete(CompletableFuture<T> result, Supplier<T> fn) {
        try {
            result.complete(fn.get());
        } catch (Exception e) {
            result.completeExceptionally(e);
        }
    }

    public static Path workspacePath() {
        var root = ResourcesPlugin.getWorkspace().getRoot();
        IPath loc = root.getRawLocation();
        if (loc == null) {
            loc = root.getLocation();
        }
        if (loc == null) {
            throw new IllegalStateException(
                    "Workspace root has no filesystem location");
        }
        return loc.toFile().toPath();
    }
    
    /**
     * Run in UI Thread!!
     */
    public static Optional<IEditorPart> getOpenEditor() {
        if (!PlatformUI.isWorkbenchRunning()) return Optional.empty();

        var aww = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (aww == null) return Optional.empty();
        var ap = aww.getActivePage();
        if (ap == null) return Optional.empty();
        return Optional.ofNullable(ap.getActiveEditor());
    }
    
    @Nonnull
    public static ITextEditor getTextEditor(IEditorPart editor) {
        if (editor instanceof ITextEditor text) {
            return text;
        } else if (editor instanceof MultiPageEditorPart multiPage) {
            var textEditor = multiPage.getAdapter(ITextEditor.class);
            
            if (textEditor == null) {
                throw new IllegalArgumentException(
                    "MultiPageEditor " + editor.getClass().getName() + " has no ITextEditor adapter. " +
                    "Please switch to the Source tab in the editor.");
            }
            return textEditor;
        } else {
            throw new IllegalArgumentException(
                "Cannot read from unknown editor " 
                        + editor.getClass().getName()
                        + " (not ITextEditor or MultiPageEditorPart)"
                
            );
        }
    }

    /**
     * Opens the given workspace file in the workbench editor. Must be called
     * from the UI thread. Throws {@link RuntimeException} if the editor cannot
     * be opened. Run in UI Thread.
     * 
     * @return the open {@link IEditorPart}, <code>null</code> if failed to open
     */
    public static IEditorPart openInEditor(IFile file) {
        if (!PlatformUI.isWorkbenchRunning()) return null;
        if (PlatformUI.getWorkbench().getActiveWorkbenchWindow() == null) return null;

        var page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        if (page == null || !file.exists()) return null;
        try {
            return IDE.openEditor(page, file, true);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Could not open editor for " + file.getFullPath(), e);
        }
    }
    
    /**
     * Run in UI Thread!!
     */
    @NonNull
    public AiFileUpdate editInEditor(IFile resource, String oldContent, String newContent) {
        IEditorPart editor = openInEditor(resource);
        var path = JdtUtil.pathOf(resource);
        if (editor == null) throw new IllegalArgumentException("Could not open " + path + " no open workbench.");

        IDocumentProvider provider = ((ITextEditor) editor).getDocumentProvider();
        IDocument document = provider.getDocument(editor.getEditorInput());
        
        var oldDoc = document.get();
        var edit = FileUtils.applyEdit(path, oldDoc, oldContent, newContent);
        document.set(edit.content());

        if (!PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().saveEditor(editor, false)) {
            if (!PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().saveEditor(editor, true)) {
                throw new IllegalStateException("Failed to save " + path);
            }
        }
        return new AiFileUpdate(path, oldDoc, edit.content());
    }

    /**
     * Returns {@code true} when the given workspace file is the currently
     * active editor. Must be called from the UI thread.
     */
    public static boolean isOpenInEditor(IFile file) {
        if (file == null)
            return false;
        return getOpenFile().map(file::equals).orElse(false);
    }

    public static IProject firstOpenOrSelectedProject() {
        var openFile = getOpenFile();
        if (openFile.isPresent()) return openFile.get().getProject();
        var open = openProjects();
        return open.isEmpty() ? null : open.getFirst();
    }

    public static Optional<IFile> getOpenFile() {
        var e = getOpenEditor();

        if (e.isPresent()) {
            IEditorInput input = e.get().getEditorInput();

            // Fast path: direct IFile adapter (works for all standard workspace
            // editors)
            var file = input.getAdapter(IFile.class);
            if (file != null) return Optional.of(file);

            // Fallback: JDT compilation unit (handles linked resources, derived
            // sources, etc.)
            ICompilationUnit cu = e.get().getAdapter(ICompilationUnit.class);
            if (cu != null && cu.getResource() instanceof IFile f) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the selected element for common structured Eclipse selections.
     */
    public static Optional<Object> selectionElement(Object value) {
        if (value == null) return Optional.empty();
        if (value instanceof ITreeSelection selection) {
            if (selection.isEmpty()) return Optional.empty();
            return Optional.ofNullable(selection.getFirstElement());
        }
        if (value instanceof IStructuredSelection selection) {
            if (selection.isEmpty()) return Optional.empty();
            return Optional.ofNullable(selection.getFirstElement());
        }
        return Optional.of(value);
    }

    /**
     * Resolves common Eclipse selection, JDT, and adaptable objects to a
     * workspace resource.
     */
    public static Optional<IResource> resolveResource(Object value) {
        var element = selectionElement(value);
        if (element.isEmpty())
            return Optional.empty();
        value = element.get();

        if (value instanceof IWorkingSet) return Optional.empty();
        if (value instanceof IResource resource) return Optional.of(resource);
        if (value instanceof ICompilationUnit compilationUnit) {
            return Optional.ofNullable(compilationUnit.getResource());
        }
        if (value instanceof IJavaProject javaProject) {
            return Optional.ofNullable(javaProject.getResource());
        }
        if (value instanceof IClassFile classFile) {
            return Optional.ofNullable(classFile.getResource());
        }
        if (value instanceof IJavaElement javaElement) {
            return Optional.ofNullable(javaElement.getResource());
        }
        if (value instanceof IAdaptable adaptable) {
            var resource = adaptable.getAdapter(IResource.class);
            if (resource != null) return Optional.of(resource);
            var javaElement = adaptable.getAdapter(IJavaElement.class);
            if (javaElement != null) return Optional.ofNullable(javaElement.getResource());
        }
        return Optional.empty();
    }

    /**
     * Resolves an absolute disk location back to a workspace resource when
     * Eclipse knows it.
     */
    public static Optional<IResource> resolveResourceFromLocation(String path) {
        if (StringUtil.hasNoValue(path))
            return Optional.empty();
        var root = ResourcesPlugin.getWorkspace().getRoot();
        var uri = Path.of(path).toUri();
        for (var file : root.findFilesForLocationURI(uri)) {
            if (file.exists())
                return Optional.of(file);
        }
        for (IContainer container : root.findContainersForLocationURI(uri)) {
            if (container.exists())
                return Optional.of(container);
        }
        return Optional.empty();
    }

    public static Optional<IProject> findOpenProject(String path) {
        if (StringUtil.hasNoValue(path))
            return Optional.empty();
        var name = Path.of(path).normalize().getName(0).toString();

        for (var p : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            if (!p.isOpen())
                continue;
            if (p.getName().equalsIgnoreCase(name)
                    || p.getFullPath().toPortableString().contains(path)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    public static List<IProject> openProjects() {
        var result = new ArrayList<IProject>();
        for (IProject p : ResourcesPlugin.getWorkspace().getRoot()
                .getProjects()) {
            if (!p.isOpen())
                continue;
            result.add(p);
        }
        return result;
    }

    public static List<IProject> openProjectsPreferring(@Nullable IProject selected) {
        var result = openProjects();
        if (selected != null && selected.isOpen() && result.remove(selected)) {
            result.add(0, selected);
        }
        return result;
    }

    public static String openProjectsNames() {
        return EclipseUtil.openProjects().stream().map(IProject::getName)
                .collect(Collectors.joining(", "));
    }

    public static String projectNatures(IProject project) {
        try {
            String[] ids = project.getDescription().getNatureIds();
            if (ids.length == 0)
                return "none";
            var sb = new StringBuilder();
            for (String id : ids) {
                if (!sb.isEmpty())
                    sb.append(", ");
                // show short name for well-known natures, full id otherwise
                sb.append(switch (id) {
                    case "org.eclipse.jdt.core.javanature" -> "java";
                    case "org.eclipse.pde.PluginNature" -> "pde-plugin";
                    case "org.eclipse.m2e.core.maven2Nature" -> "maven";
                    case "org.eclipse.buildship.core.gradleprojectnature" ->
                        "gradle";
                    default -> id;
                });
            }
            return sb.toString();
        } catch (CoreException e) {
            return "unknown";
        }
    }
    public static String projectInfo(IProject p) {
        final var result = new StringBuilder();
        result.                             append("Project name: ").append(p.getName()) //
            .append(System.lineSeparator()).append("Eclipse path: ").append(JdtUtil.pathOf(p)) //
            .append(System.lineSeparator()).append("Disk path:    ").append(JdtUtil.diskPathOf(p)) //
            .append(System.lineSeparator()).append("Natures:      ").append(projectNatures(p)); //

        var m = findMember(p, "pom.xml");
        if (m.isPresent()) result.append(System.lineSeparator()).append(JdtUtil.pathOf(m.get()));
        m = findMember(p, "package.json");
        if (m.isPresent()) result.append(System.lineSeparator()).append(JdtUtil.pathOf(m.get()));

        return result.toString();
    }

    /**
     * Resolves a path to a workspace resource (file or folder). Tries
     * workspace-relative first, then project-relative in each open project.
     */
    public static Optional<IResource> resolveInEclipse(String path) {
        if (StringUtil.hasNoValue(path))
            return Optional.empty();
        IPath ipath = IPath.fromPortableString(path);
        try {
            var result = ResourcesPlugin.getWorkspace().getRoot()
                    .findMember(ipath);
            if (result != null && result.exists())
                return Optional.of(result);
        } catch (Exception e) {
            // invalid workspace path, continue
        }

        for (var p : openProjects()) {
            var result = p.findMember(ipath);
            if (result != null && result.exists())
                return Optional.of(result);
            // java src fallback
            result = p.findMember("src/" + ipath);
            if (result != null && result.exists())
                return Optional.of(result);
        }
        return Optional.empty();
    }

    public static Optional<IFile> findMember(IContainer root, String path) {
        var f = root.findMember(path);
        if (f != null && f instanceof IFile ff)
            return Optional.of(ff);
        return Optional.empty();
    }

    /**
     * Extracts the project from a resource selection.
     */
    public static IProject resolveProject(IResource selection) {
        if (selection == null)
            return null;
        return selection.getProject();
    }

    /**
     * Recursively searches all open projects for the first file matching the
     * given name.
     */
    public static Optional<IFile> searchWorkspaceFiles(String fileName) {
        if (StringUtil.hasNoValue(fileName))
            return Optional.empty();
        for (var p : openProjects()) {
            Optional<IFile> hit = searchProject(p, fileName);
            if (hit.isPresent())
                return hit;
        }
        return Optional.empty();
    }

    private static Optional<IFile> searchProject(IProject project,
            String fileName) {
        try {
            AtomicReference<IFile> result = new AtomicReference<>();
            project.accept(new IResourceVisitor() {
                @Override
                public boolean visit(IResource resource) {
                    if (result.get() != null)
                        return false; // stop after first hit
                    if (resource.getType() == IResource.FILE
                            && resource.getName().equals(fileName)) {
                        result.set((IFile) resource);
                        return false;
                    }
                    return true;
                }
            });
            return Optional.ofNullable(result.get());
        } catch (CoreException e) {
            return Optional.empty();
        }
    }

}
