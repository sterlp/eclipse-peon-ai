package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.parts.ai.PeonAiService;
import org.sterl.llmpeon.parts.tools.PlanTool;

/**
 * Archive behaviour of {@link PlanTool} (org.sterl.llmpeon/docs/plan-tool.md R-PT-1): a plan written
 * past the workspace tree by the disk tools must still archive, because the agent gets no second
 * signal that the tree never learned about the file.
 */
public class PlanToolTest extends AbstractIntegrationTest {

    private PeonAiService aiService;

    @Before
    public void wirePlanTool() {
        var ccm = new ConfiguredChatModel(LlmConfig.builder()
                .model("test")
                .url("http://localhost:0")
                .configDir(Path.of(System.getProperty("java.io.tmpdir"), ".peon-test"))
                .build());
        aiService = new PeonAiService(() -> {}, null, null, null, ccm);
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
        aiService.getSharedToolService().getTool(PlanTool.class).orElseThrow().planImplemented();

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
        aiService.getSharedToolService().getTool(PlanTool.class).orElseThrow().planImplemented();

        // THEN archived with its current disk content, and no RuntimeException around the move
        var archives = archives();
        assertEquals("expected exactly one archive in " + planDir(), 1, archives.size());
        assertFalse(Files.exists(overview));
        assertContains(Files.readString(archives.get(0), StandardCharsets.UTF_8), "rewritten on disk");
    }

    // R-PT-2
    // UC-PT-2
    @Test
    public void planReadSeesDiskWrittenPlan() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        deletePlanDir();

        // GIVEN overview.md on disk, unknown to the workspace tree
        var planTool = aiService.getSharedToolService().getTool(PlanTool.class).orElseThrow();
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
        var planTool = aiService.getSharedToolService().getTool(PlanTool.class).orElseThrow();
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
        var planTool = aiService.getSharedToolService().getTool(PlanTool.class).orElseThrow();
        Path overview = givenDiskWrittenPlan("# alpha\n");

        // WHEN a refinement is applied
        planTool.planUpdate("alpha", "beta");

        // THEN it lands on the disk plan instead of failing on a plan it claims not to have
        assertContains(Files.readString(overview, StandardCharsets.UTF_8), "beta");
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
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }
}
