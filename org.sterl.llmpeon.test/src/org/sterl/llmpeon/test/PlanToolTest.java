package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.parts.ai.PeonAiService;
import org.sterl.llmpeon.parts.shared.IoUtils;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.parts.tools.PlanFileView;
import org.sterl.llmpeon.parts.tools.PlanTool;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.model.SimpleMessage;

/**
 * Plan file handling of {@link PlanTool} (org.sterl.llmpeon/docs/plan-tool.md): plans written past the
 * workspace tree by the disk tools must still be read and archived, and a failing archive must never
 * pass silently — the agent gets no second signal either way.
 */
public class PlanToolTest extends AbstractIntegrationTest {

    private PeonAiService aiService;
    private ConfiguredChatModel chatModel;

    @Before
    public void wirePlanTool() {
        chatModel = new ConfiguredChatModel(LlmConfig.builder()
                .model("test")
                .url("http://localhost:0")
                .configDir(Path.of(System.getProperty("java.io.tmpdir"), ".peon-test"))
                .build());
        aiService = new PeonAiService(() -> {}, null, null, null, chatModel);
        aiService.setProject(project);
        aiService.clearAll();
    }

    @After
    public void releasePlanTool() throws Exception {
        deletePlanDir();
        PeonAiService.clearCurrent();
    }

    // R-PT-1
    // UC-PT-1
    @Test
    public void planImplementedArchivesDiskWrittenPlan() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        deletePlanDir();

        // GIVEN overview.md written straight to disk, so the workspace tree does not know the file
        Path overview = givenDiskWrittenPlan("# disk written plan\n");

        // WHEN the agent reports the plan as implemented
        planTool().planImplemented();

        // THEN it is archived under overview-done-<stamp>.md — no ResourceException, overview.md gone
        var archives = archives();
        assertEquals("expected exactly one archive in " + planDir(), 1, archives.size());
        String archived = archives.get(0).getFileName().toString();
        assertTrue(archived, archived.startsWith("overview-done-") && archived.endsWith(".md"));
        assertFalse("the plan itself must not survive the archive", Files.exists(overview));
        assertTrue("the archive must exist in the workspace tree too",
                project.getFile(PlanTool.PLAN_DIR + "/" + archived).exists());
    }

    // R-PT-1
    // UC-PT-1
    @Test
    public void planImplementedArchivesPlanTheTreeHasOutOfSync() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        deletePlanDir();

        // GIVEN a plan the tree knows, whose disk content was then changed past the tree (disk tools) —
        // this is the variant that failed with ResourceException, because move() refuses out-of-sync files
        Path overview = planDir().resolve("overview.md");
        eclipseWriteFile("/" + PeonTestFixture.PROJECT_NAME + "/" + PlanTool.OVERVIEW_FILE, "# written by planSave\n");
        Files.writeString(overview, "# rewritten on disk\n", StandardCharsets.UTF_8);
        // the explicit mtime bump is what makes the tree stale — a plain rewrite can land inside the
        // file-system timestamp granularity and leave the resource looking synchronized
        Files.setLastModifiedTime(overview, FileTime.fromMillis(System.currentTimeMillis() + 60_000));
        var plan = project.getFile(PlanTool.OVERVIEW_FILE);
        assertFalse("fixture must leave the plan out of sync with the tree, otherwise R-PT-1 is not proven here",
                plan.isSynchronized(IResource.DEPTH_ZERO));

        // WHEN
        planTool().planImplemented();

        // THEN archived with its current disk content, and no RuntimeException around the move
        var archives = archives();
        assertEquals("expected exactly one archive in " + planDir(), 1, archives.size());
        assertFalse(Files.exists(overview));
        assertContains(Files.readString(archives.get(0), StandardCharsets.UTF_8), "rewritten on disk");
    }

    // R-PT-1
    // UC-PT-1
    @Test
    public void planImplementedNamesPathAndCauseWhenTheMoveFails() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        deletePlanDir();

        // GIVEN a plan whose archive directory refuses writes, so move() has to fail with a CoreException.
        // A user Stop is deliberately not the fixture here: it aborts with OperationCanceledException,
        // which is a RuntimeException and never reaches the CoreException handler under test
        Path overview = givenDiskWrittenPlan("# disk written plan\n");
        var archiveDir = planDir().toFile();
        assumeTrue("needs a file store where a read-only directory blocks the move",
                archiveDir.setWritable(false, false) && !archiveDir.canWrite());
        try {
            // WHEN
            planTool().planImplemented();
            fail("a failed archive must never pass silently");
        } catch (RuntimeException e) {
            // THEN path and cause are named, so the agent can act instead of guessing
            String info = e + " | cause: " + e.getCause();
            assertTrue(info, info.contains("/" + PeonTestFixture.PROJECT_NAME + "/" + PlanTool.OVERVIEW_FILE));
            assertTrue(info, e.getCause() instanceof CoreException);
            // AND the plan survives — a half-done archive has to stay recoverable
            assertTrue("a failed archive must leave the plan in place for a retry", Files.exists(overview));
            assertTrue("nothing may be half-archived, but found " + archives(), archives().isEmpty());
        } finally {
            archiveDir.setWritable(true, false);
        }
    }

    // R-PT-2
    // UC-PT-2
    @Test
    public void planReadSeesDiskWrittenPlan() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        deletePlanDir();

        // GIVEN overview.md on disk, unknown to the workspace tree
        var planTool = planTool();
        givenDiskWrittenPlan("# disk written plan\n");

        // WHEN the agent reads its plan
        String content = planTool.planRead();

        // THEN the real content — never the false negative the agent would act on
        assertContains(content, "disk written plan");
        assertFalse("must not report a missing plan, but got: " + content, content.contains("No plan exists"));
    }

    // R-PT-2
    // UC-PT-2
    @Test
    public void hasPlanSeesDiskWrittenPlan() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        deletePlanDir();

        // GIVEN
        var planTool = planTool();
        givenDiskWrittenPlan("# disk written plan\n");

        // WHEN / THEN the path is offered to buildWithDev instead of "No plan exists yet."
        assertTrue("a disk-written plan must never be reported as missing", planTool.hasPlan());
    }

    // R-PT-2
    // UC-PT-2
    @Test
    public void planUpdateEditsDiskWrittenPlan() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        deletePlanDir();

        // GIVEN a plan the tree has never seen
        var planTool = planTool();
        Path overview = givenDiskWrittenPlan("# alpha\n");

        // WHEN a refinement is applied
        planTool.planUpdate("alpha", "beta");

        // THEN it lands on the disk plan instead of failing on a plan it claims not to have
        assertContains(Files.readString(overview, StandardCharsets.UTF_8), "beta");
    }

    // R-PT-2
    // UC-PT-2a
    @Test
    public void queryPathsFailHonestOnRefreshError() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        deletePlanDir();

        // GIVEN a plan tool whose workspace refresh is refused. exists() can never produce this —
        // refreshLocal's one documented CoreException is a blocked resource change — so the refresh
        // itself has to be injected for the failure path to be testable at all.
        var problems = new ArrayList<String>();
        var planTool = planTool((resource, depth, monitor) -> {
            throw new CoreException(new Status(IStatus.ERROR, "test", "refresh blocked"));
        }, problems);

        // WHEN the turn path asks whether a plan exists (onAgentChange -> preloadPlanIfNeeded)
        assertFalse("a failed refresh must not claim a plan", planTool.hasPlan());

        // THEN the turn survives and the chat names path and cause — an honest problem, never a
        // silent no and never an exception after the agent switch already happened
        assertEquals(problems.toString(), 1, problems.size());
        assertContains(problems.get(0), "/" + PeonTestFixture.PROJECT_NAME + "/" + PlanTool.OVERVIEW_FILE);
        assertContains(problems.get(0), "refresh blocked");

        // AND reading answers the same way: honest text, one further problem, no throw
        assertContains(planTool.planRead(), "No plan exists yet");
        assertEquals(problems.toString(), 2, problems.size());
        assertContains(problems.get(1), "refresh blocked");
    }

    // R-PT-2
    // UC-PT-2a
    @Test
    public void queryPathRefreshesOnlyThePlanFileDepthZero() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        deletePlanDir();

        // GIVEN a disk-written plan and a refresh spy that still performs the real refresh
        givenDiskWrittenPlan("# disk written plan\n");
        var refreshed = new ArrayList<String>();
        var planTool = planTool((resource, depth, monitor) -> {
            resource.refreshLocal(depth, monitor);
            refreshed.add(JdtUtil.pathOf(resource) + " depth=" + depth);
        }, new ArrayList<>());

        // WHEN both query paths run
        assertContains(planTool.planRead(), "disk written plan");
        assertTrue(planTool.hasPlan());

        // THEN each query refreshes exactly the plan file — the plan is visible without the folder,
        // and the per-turn cost stays a single file (DEPTH_INFINITE on the folder would re-read every
        // archive on every turn, and is only right for the archive path)
        var planOnly = "/" + PeonTestFixture.PROJECT_NAME + "/" + PlanTool.OVERVIEW_FILE
                + " depth=" + IResource.DEPTH_ZERO;
        assertEquals(List.of(planOnly, planOnly), refreshed);
    }

    /**
     * R-PT-1: the recursive folder refresh belongs to the archive alone — it needs the whole plan dir
     * in sync (plan plus the archive names already taken). Pinned with the query depth (UC-PT-2a) as
     * the contrast: DEPTH_INFINITE here, DEPTH_ZERO on the file there.
     */
    // R-PT-1
    // UC-PT-1
    @Test
    public void archiveRefreshesThePlanFolderRecursively() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        deletePlanDir();

        // GIVEN a disk-written plan and a refresh spy that still performs the real refresh
        givenDiskWrittenPlan("# disk written plan\n");
        var refreshed = new ArrayList<String>();
        var planTool = planTool((resource, depth, monitor) -> {
            resource.refreshLocal(depth, monitor);
            refreshed.add(JdtUtil.pathOf(resource) + " depth=" + depth);
        }, new ArrayList<>());

        // WHEN the agent reports the plan as implemented
        planTool.planImplemented();

        // THEN exactly one refresh, on the folder, recursive — DEPTH_ZERO or the file would leave the
        // archive target out of sync and re-introduce the ResourceException of R-PT-1
        assertEquals(List.of("/" + PeonTestFixture.PROJECT_NAME + "/" + PlanTool.PLAN_DIR
                + " depth=" + IResource.DEPTH_INFINITE), refreshed);
        // AND the archive really ran, so the call above is the archive path and not an early return
        assertEquals(archives().toString(), 1, archives().size());
    }

    /**
     * R-PT-6 (org.sterl.llmpeon/docs/plan-tool.md): the query must never open the project. The
     * production path ran into {@code open()} and exploded a ResourceException through the UI event
     * loop on every selection change (phantom handle of an externally opened file, 2026-11-03).
     */
    // UC-PT-6
    @Test
    public void planQueryNeverOpensTheProject() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        // GIVEN a phantom handle bound straight on the user context — deliberately past the admission
        // guard of setProject (R-SEL-5), because that very binding is the crash precondition
        var phantom = phantomHandle("phantom_plan_project");
        aiService.getUserContext().setCurrentProject(phantom);
        try {
            // WHEN both query paths run with the production refresh
            // THEN false/null instead of an exception: on an inaccessible project refreshLocal is a
            // documented no-op and exists() is false, so the query answers "no plan" honestly. A
            // thrown RuntimeException here is the red test; problems are deliberately not asserted,
            // because "no plan" without a problem is the truth for a phantom, not a swallowed error.
            assertFalse("a phantom must never claim a plan", planTool().hasPlan());
            assertNull("a phantom must never hand out a plan file", planTool().visiblePlan());

            // AND the other honest branch of the SOLL: a failing refresh reports and still answers
            // "no plan" — never a throw, never a silent no
            var problems = new ArrayList<String>();
            var planTool = planTool((resource, depth, monitor) -> {
                throw new CoreException(new Status(IStatus.ERROR, "test", "refresh blocked"));
            }, problems);
            assertFalse("a failed refresh must not claim a plan", planTool.hasPlan());
            assertEquals(problems.toString(), 1, problems.size());
            assertContains(problems.get(0), "/phantom_plan_project/" + PlanTool.OVERVIEW_FILE);
            assertContains(problems.get(0), "refresh blocked");
        } finally {
            aiService.getUserContext().setCurrentProject(project);
        }
    }

    /**
     * R-PT-7 (org.sterl.llmpeon/docs/plan-tool.md): the tool path may still open a project, but only
     * after exists() — the agent gets an actionable message with the project name instead of the raw
     * "Resource '/home' does not exist" of open().
     */
    // UC-PT-7
    @Test
    public void planToolPathFailsWithProjectNameForPhantomHandle() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        // GIVEN the same phantom handle as the bound project
        var phantom = phantomHandle("phantom_plan_project");
        aiService.getUserContext().setCurrentProject(phantom);
        try {
            // WHEN
            try {
                planTool().planSave("# x\n");
                fail("a phantom project must never be written to");
            } catch (IllegalStateException e) {
                // THEN the message names the project so the agent can ask for a real selection
                assertTrue(e.getMessage(), e.getMessage().contains("phantom_plan_project"));
                // AND it is not the raw open() failure wrapped
                assertFalse("must not be the open() ResourceException, but was: " + e.getCause(),
                        e.getCause() instanceof CoreException);
            }
        } finally {
            aiService.getUserContext().setCurrentProject(project);
        }
    }

    /**
     * The other half of R-PT-7: opening stays a FEATURE for a real, closed project — without this
     * assertion the exists()-check could be "fixed" by dropping open() altogether.
     */
    // UC-PT-7
    @Test
    public void planToolPathStillOpensAClosedExistingProject() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        // GIVEN a real project that exists but is closed, bound as the plan project
        var closed = ResourcesPlugin.getWorkspace().getRoot().getProject("aaa_closed_plan");
        if (closed.exists()) closed.delete(true, true, new NullProgressMonitor());
        closed.create(new NullProgressMonitor());
        closed.open(new NullProgressMonitor());
        closed.close(new NullProgressMonitor());
        assumeTrue("fixture needs a closed but existing project", closed.exists() && !closed.isOpen());
        aiService.setProject(closed);
        try {
            // WHEN
            planTool().planSave("# closed\n");

            // THEN the plan landed and the project was opened on the way
            assertTrue("the tool path must still open a closed real project", closed.isOpen());
            assertTrue("the plan must be written into it", closed.getFile(PlanTool.OVERVIEW_FILE).exists());
        } finally {
            aiService.setProject(project);
            if (closed.exists()) {
                closed.open(new NullProgressMonitor());
                closed.delete(true, true, new NullProgressMonitor());
            }
        }
    }

    /**
     * R-PT-7 Präzisierung (Review 2026-11-05, B1): {@code planRead} is a TOOL path, so it opens the
     * bound project — a query-only read would answer "No plan exists yet" for a plan that exists.
     */
    // UC-PT-2
    @Test
    public void planReadSeesThePlanOfAClosedProject() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        // GIVEN a closed, real project with the plan already in place
        var closed = closedProjectWithPlan("aaa_closed_read", "# closed plan\n");
        aiService.setProject(closed);
        try {
            // WHEN
            var read = planTool().planRead();

            // THEN the tool path reads the plan instead of denying it — and opened the project for it
            assertContains(read, "closed plan");
            assertFalse("must never deny an existing plan: " + read, read.contains("No plan exists"));
            assertTrue("the tool path must open the closed project", closed.isOpen());
        } finally {
            aiService.setProject(project);
            deleteProject(closed);
        }
    }

    /**
     * R-PT-8 (org.sterl.llmpeon/docs/plan-tool.md): the query may not open, so it cannot see the plan
     * of a closed project — it names the project instead of silently denying it (a false negative is
     * the most expensive answer in this codebase).
     */
    // UC-PT-8
    @Test
    public void closedProjectIsNamedByTheQuery() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        // GIVEN the same closed fixture, bound as the plan project
        var closed = closedProjectWithPlan("aaa_closed_query", "# closed plan\n");
        aiService.setProject(closed);
        var problems = new ArrayList<String>();
        try {
            var planTool = planTool(IResource::refreshLocal, problems);

            // WHEN the query runs with the production refresh
            assertFalse("a closed project must not claim a plan", planTool.hasPlan());

            // THEN it reports instead of denying silently, and names the project
            assertEquals(problems.toString(), 1, problems.size());
            assertContains(problems.get(0), "aaa_closed_query");
            assertContains(problems.get(0), "closed");

            // AND the second query path answers the same way
            assertNull("a closed project must not hand out a plan file", planTool.visiblePlan());
            assertEquals(problems.toString(), 2, problems.size());
            for (var problem : problems) {
                assertContains(problem, "aaa_closed_query");
                assertContains(problem, "closed");
            }
            // AND naming it is all the query may do — opening stays the tool path (R-PT-6)
            assertFalse("the query must not open the project", closed.isOpen());
        } finally {
            aiService.setProject(project);
            deleteProject(closed);
        }
    }

    /**
     * UC-PT-8 second BDD point: the closed report must not swallow the phantom case — a handle
     * without a resource has nothing closed about it and stays silent (R-PT-2a).
     */
    // UC-PT-8
    @Test
    public void phantomHandleStaysSilentInTheQuery() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        // GIVEN a phantom bound straight on the user context, past the admission guard (R-SEL-5)
        var phantom = phantomHandle("phantom_query_silent");
        aiService.getUserContext().setCurrentProject(phantom);
        var problems = new ArrayList<String>();
        try {
            var planTool = planTool(IResource::refreshLocal, problems);

            // WHEN both query paths run
            assertFalse("a phantom must never claim a plan", planTool.hasPlan());
            assertNull("a phantom must never hand out a plan file", planTool.visiblePlan());

            // THEN no plan and no report — "closed" would be a lie for a phantom
            assertEquals(problems.toString(), 0, problems.size());
        } finally {
            aiService.getUserContext().setCurrentProject(project);
        }
    }

    /** A real project that exists but is closed, with a plan already in place. */
    private static IProject closedProjectWithPlan(String name, String planContent) throws CoreException {
        var p = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
        if (p.exists()) p.delete(true, true, new NullProgressMonitor());
        p.create(new NullProgressMonitor());
        p.open(new NullProgressMonitor());
        IoUtils.writeProjectFile(p, PlanTool.OVERVIEW_FILE, planContent, new NullProgressMonitor());
        p.close(new NullProgressMonitor());
        assumeTrue("fixture needs a closed but existing project", p.exists() && !p.isOpen());
        return p;
    }

    private static void deleteProject(IProject p) throws CoreException {
        if (!p.exists()) return;
        p.open(new NullProgressMonitor());
        p.delete(true, true, new NullProgressMonitor());
    }

    /** A handle for a project that is not in the workspace tree. */
    private static IProject phantomHandle(String name) {
        var phantom = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
        assertFalse("fixture must be a phantom handle without a project", phantom.exists());
        return phantom;
    }

    private PlanTool planTool() {
        return aiService.getSharedToolService().getTool(PlanTool.class).orElseThrow();
    }

    /**
     * A tool with an injected workspace refresh: neither the failure nor the refresh depth is
     * reachable through the real API, and both are exactly what UC-PT-2a pins.
     */
    private PlanTool planTool(PlanFileView.Refresh refresh, List<String> problems) {
        var tool = new PlanTool(aiService, refresh);
        tool.withToolRequest(ToolLoopRequest.builder()
                .memory(new ThreadSafeMemory())
                .chatModel(chatModel)
                .monitor(new AiMonitor() {
                    @Override public void onChatResponse(SimpleMessage m) {}
                    @Override public void onProblem(String message) { problems.add(message); }
                })
                .build());
        return tool;
    }

    /** Writes the plan past the workspace tree and proves the fixture really left the tree stale. */
    private static Path givenDiskWrittenPlan(String content) throws Exception {
        Path overview = Files.createDirectories(planDir()).resolve("overview.md");
        Files.writeString(overview, content, StandardCharsets.UTF_8);
        assertFalse("fixture must leave the workspace tree stale, otherwise the refresh is not proven here",
                project.getFile(PlanTool.OVERVIEW_FILE).exists());
        return overview;
    }

    private static Path planDir() {
        return PeonTestFixture.dir().toPath().resolve(PlanTool.PLAN_DIR);
    }

    private static List<Path> archives() throws Exception {
        try (Stream<Path> files = Files.list(planDir())) {
            return files.filter(p -> p.getFileName().toString().startsWith("overview-done-")).toList();
        }
    }

    /** Removes the plan dir from the tree AND from disk — the next run must start from a clean fixture. */
    private static void deletePlanDir() throws Exception {
        var folder = project.getFolder(PlanTool.PLAN_DIR);
        if (folder.exists()) {
            folder.delete(IResource.FORCE, new NullProgressMonitor());
        }
        Path dir = planDir();
        if (!Files.exists(dir)) return;
        dir.toFile().setWritable(true, false);
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }
}
