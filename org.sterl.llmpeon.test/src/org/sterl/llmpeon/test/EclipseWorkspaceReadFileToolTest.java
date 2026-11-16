package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.junit.Test;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.parts.tools.EclipseBuildTool;
import org.sterl.llmpeon.parts.tools.EclipseCodeNavigationTool;
import org.sterl.llmpeon.parts.tools.EclipseGrepTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceReadFileTool;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

public class EclipseWorkspaceReadFileToolTest extends AbstractIntegrationTest {

    @Test
    public void test_findReferences() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var tool = new EclipseCodeNavigationTool();

        var content = tool.eclipseFindReferences(
                "org.sterl.fixture", "Alpha", null, PeonTestFixture.PROJECT_NAME);

        assertContains(content, "Beta.java");
        assertContains(content, "5");
    }

    @Test
    public void testList() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var tool = new EclipseBuildTool();

        var result = tool.eclipseListAllOpenProjects();

        assertTrue("Fixture project not found:\n" + result, result.contains(PeonTestFixture.PROJECT_NAME));
    }

    @Test
    public void test_getTypeSource() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var tool = new EclipseCodeNavigationTool();

        var content = tool.eclipseReadTypeSource(
                "org.sterl.fixture", "Alpha", PeonTestFixture.PROJECT_NAME);

        assertContains(content, "public String hello()");
        assertContains(content, " 1: ");
        assertContains(content, "Alpha.java");
    }

    @Test
    public void test_getTypeSource_wrong_package() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var tool = new EclipseCodeNavigationTool();

        var content = tool.eclipseReadTypeSource(
                "foo.bar", "Alpha", PeonTestFixture.PROJECT_NAME);

        assertContains(content, "Alpha.java");
    }

    @Test
    public void readUtf8() throws Exception {
        var tool = new EclipseWorkspaceReadFileTool();

        var content = tool.eclipseReadFile(
                JdtUtil.pathOf(project) + "/data/utf-8-test.txt", null, null);

        assertEquals("   1: äüß Ö ⚡\n", content);
    }

    @Test
    public void readIso() throws Exception {
        var tool = new EclipseWorkspaceReadFileTool();

        var content = tool.eclipseReadFile(
                JdtUtil.pathOf(project) + "/data/iso-test.txt", null, null);

        assertEquals("   1: äüß Ö\n", content);
    }

    @Test
    public void test_grepWorkspaceFiles() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var tool = new EclipseGrepTool();

        var content = tool.eclipseGrepFiles(
                "grepMe", PeonTestFixture.PROJECT_NAME, ".java");

        assertTrue("Should contain fixture grep target:\n" + content,
                content.contains("GrepTarget.java"));
    }

    @Test
    public void test_grepWorkspaceFiles_regexPattern() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var tool = new EclipseGrepTool();

        var content = tool.eclipseGrepFiles(
                "class.*Alpha", PeonTestFixture.PROJECT_NAME, ".java");

        assertTrue("Regex should match fixture class declaration:\n" + content,
                content.contains("Alpha.java"));
    }

    @Test
    public void test_grepWorkspaceFiles_regexAlternation() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var tool = new EclipseGrepTool();

        var content = tool.eclipseGrepFiles(
                "class AlphaBeta|class Alphabet", PeonTestFixture.PROJECT_NAME, ".java");

        assertTrue("Regex alternation should match fixture classes:\n" + content,
                content.contains("AlphaBeta.java") && content.contains("Alphabet.java"));
    }

    @Test
    public void test_readWorkspaceFiles() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        ToolService service = new ToolService();
        service.addTool(new EclipseWorkspaceReadFileTool());

        var request = ToolExecutionRequest.builder()
            .name("eclipseReadFile")
            .arguments("{\"filePath\": \"/test_project/data/lines-120.txt\"}")
            .build();

        var content = service.execute(request,
                ToolLoopRequest.builder()
                    .memory(new ThreadSafeMemory())
                    .chatModel(new ConfiguredChatModel(LlmConfig.newOpenAi("foo")))
                    .build());

        assertContains(content.text(), "line 1");
        assertContains(content.text(), "line 120");
    }

    @Test
    public void readFileOnToolLineStartsWithScrollSymbol() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        ToolService service = new ToolService();
        service.addTool(new EclipseWorkspaceReadFileTool());

        var lines = new ArrayList<String>();
        var monitor = new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public void onTool(String message) { lines.add(message); }
        };

        var request = ToolExecutionRequest.builder()
            .name("eclipseReadFile")
            .arguments("{\"filePath\": \"" + JdtUtil.pathOf(project) + "/data/lines-120.txt\"}")
            .build();

        var content = service.execute(request,
                ToolLoopRequest.builder()
                    .memory(new ThreadSafeMemory())
                    .chatModel(new ConfiguredChatModel(LlmConfig.newOpenAi("foo")))
                    .monitor(monitor)
                    .build());

        assertContains(content.text(), "line 1");
        assertEquals(1, lines.size());
        assertTrue("onTool line should start with the scroll symbol: " + lines.get(0),
                lines.get(0).startsWith("📄 "));
    }

    /**
     * A file written past the workspace tree (disk tool, shell, another editor) has to show up in
     * the listing: {@code eclipseList} is a claim about what exists, and a silent miss is the most
     * expensive tool error of this repo — the agent then acts on "does not exist".
     */
    @Test
    public void eclipseListSeesFileWrittenOutsideEclipse() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var tool = new EclipseWorkspaceReadFileTool();
        String name = "eclipse-list-fresh.txt";
        Path fresh = PeonTestFixture.dir().toPath().resolve(name);

        try {
            // GIVEN a file written straight into the project folder, unknown to the workspace tree
            Files.writeString(fresh, "written past the workspace tree\n");
            assertFalse("fixture must leave the tree stale, otherwise the refresh is not proven here",
                    project.getFile(name).exists());

            // WHEN the agent lists the project folder
            String result = tool.eclipseList(JdtUtil.pathOf(project));

            // THEN the file is named — never a listing that quietly omits what is on disk
            assertContains(result, name);
        } finally {
            Files.deleteIfExists(fresh);
            project.refreshLocal(IResource.DEPTH_ONE, new NullProgressMonitor());
        }
    }

    /**
     * A refused refresh must not leave the listing claiming completeness: the result names the
     * limitation, the problem line names path and cause. Neither is reachable through the real API,
     * so the refresh is injected (PlanToolTest.queryPathsFailHonestOnRefreshError pattern).
     */
    @Test
    public void eclipseListReportsAFailedRefreshHonestly() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var problems = new ArrayList<String>();
        var tool = new EclipseWorkspaceReadFileTool((resource, depth, monitor) -> {
            throw new CoreException(new Status(IStatus.ERROR, "test", "refresh blocked"));
        });
        tool.withToolRequest(ToolLoopRequest.builder()
                .memory(new ThreadSafeMemory())
                .chatModel(new ConfiguredChatModel(LlmConfig.newOpenAi("foo")))
                .monitor(new AiMonitor() {
                    @Override public void onChatResponse(SimpleMessage m) {}
                    @Override public void onProblem(String message) { problems.add(message); }
                })
                .build());

        // GIVEN a listing whose workspace refresh is refused
        String result = tool.eclipseList(JdtUtil.pathOf(project));

        // THEN the listing still answers, but says it may be incomplete — never a silent "this is all"
        assertContains(result, "refresh failed");
        assertContains(result, "/src");

        // AND the chat names path and cause of the failed refresh
        assertEquals(problems.toString(), 1, problems.size());
        assertContains(problems.get(0), JdtUtil.pathOf(project));
        assertContains(problems.get(0), "refresh blocked");
    }

    // UC-RT-2
    @Test
    public void readMissWithoutMatchesKeepsOriginalMessage() {
        var refreshCalls = new int[1];
        var refreshed = new ArrayList<IProject>();
        var tool = new EclipseWorkspaceReadFileTool() {
            @Override
            protected void refreshScope(List<IProject> scope) {
                refreshCalls[0]++;
                refreshed.addAll(scope);
                super.refreshScope(scope);
            }
        };
        tool.setCurrentProject(project);

        var sep = System.lineSeparator();
        var neighborPath = "/" + PeonTestFixture.PROJECT_NAME + "/rt-nope/lines-120.txt";
        var neighborMiss = "No eclipse file found for '" + neighborPath + "'." + EclipseUtil.unresolvedHint(neighborPath);
        assertEquals(neighborMiss + sep + "Did you mean:" + sep
                + "/" + PeonTestFixture.PROJECT_NAME + "/data/lines-120.txt",
                tool.eclipseReadFile(neighborPath, null, null));
        assertEquals("No refresh for positive match", 0, refreshCalls[0]);

        var missingPath = "/" + PeonTestFixture.PROJECT_NAME + "/rt-nope/RtSuggestionMissing_20261116.txt";
        var missingMiss = "No eclipse file found for '" + missingPath + "'." + EclipseUtil.unresolvedHint(missingPath);
        assertEquals("Expected exact old miss without suggestions",
                missingMiss, tool.eclipseReadFile(missingPath, null, null));
        assertEquals("One refresh for empty search", 1, refreshCalls[0]);
        assertEquals("Refreshed only selected project", List.of(project), refreshed);
    }

    @Test
    public void readMissWithBlankSegmentDoesNotSearch() {
        var searchCalls = new int[1];
        var searchQueries = new ArrayList<String>();
        var tool = new EclipseWorkspaceReadFileTool() {
            @Override
            protected SearchResult searchFiles(String query, String projectName, int limit) {
                searchCalls[0]++;
                searchQueries.add(query);
                return super.searchFiles(query, projectName, limit);
            }
        };
        tool.setCurrentProject(project);

        var sep = System.lineSeparator();
        var neighborPath = "/" + PeonTestFixture.PROJECT_NAME + "/rt-nope/lines-120.txt";
        var neighborMiss = "No eclipse file found for '" + neighborPath + "'." + EclipseUtil.unresolvedHint(neighborPath);
        assertEquals(neighborMiss + sep + "Did you mean:" + sep
                + "/" + PeonTestFixture.PROJECT_NAME + "/data/lines-120.txt",
                tool.eclipseReadFile(neighborPath, null, null));
        assertEquals("Only one search call for neighbor", 1, searchCalls[0]);
        assertEquals("Query is last segment", "lines-120.txt", searchQueries.get(0));

        searchCalls[0] = 0;
        searchQueries.clear();

        var blankSlash = "/" + PeonTestFixture.PROJECT_NAME + "/rt-nope/";
        var blankMiss = "No eclipse file found for '" + blankSlash + "'." + EclipseUtil.unresolvedHint(blankSlash);
        assertEquals("Blank segment returns exact old miss", blankMiss,
                tool.eclipseReadFile(blankSlash, null, null));
        assertEquals("No search for blank segment", 0, searchCalls[0]);

        var blankSpaces = "/" + PeonTestFixture.PROJECT_NAME + "/rt-nope/   ";
        var blankSpacesMiss = "No eclipse file found for '" + blankSpaces + "'." + EclipseUtil.unresolvedHint(blankSpaces);
        assertEquals("Blank-with-spaces segment returns exact old miss", blankSpacesMiss,
                tool.eclipseReadFile(blankSpaces, null, null));
        assertEquals("No search for blank-with-spaces segment", 0, searchCalls[0]);
    }

    @Test
    public void readMissRequestsFourMatchesAndDisclosesAdditionalResults() throws Exception {
        var searchArgsQuery = new String[1];
        var searchArgsProject = new String[1];
        var searchArgsLimit = new int[1];
        var searchCallCount = new int[1];
        var rawPaths = new ArrayList<String>();
        var tool = new EclipseWorkspaceReadFileTool() {
            @Override
            protected SearchResult searchFiles(String query, String projectName, int limit) {
                searchCallCount[0]++;
                searchArgsQuery[0] = query;
                searchArgsProject[0] = projectName;
                searchArgsLimit[0] = limit;
                var result = super.searchFiles(query, projectName, limit);
                rawPaths.addAll(result.paths());
                return result;
            }
        };
        tool.setCurrentProject(project);

        var fileNames = new String[] {
            "RtSuggestionCap_0.txt", "RtSuggestionCap_1.txt", "RtSuggestionCap_2.txt", "RtSuggestionCap_3.txt"
        };
        for (String name : fileNames) {
            eclipseWriteFile(
                    "/" + PeonTestFixture.PROJECT_NAME + "/" + name,
                    "rtSuggestionContentMustNotBeRead");
        }

        var sep = System.lineSeparator();
        var missPath = "/" + PeonTestFixture.PROJECT_NAME + "/rt-nope/RtSuggestionCap_*.txt";
        var expectedMiss = "No eclipse file found for '" + missPath + "'." + EclipseUtil.unresolvedHint(missPath);
        String result = tool.eclipseReadFile(missPath, null, null);

        assertEquals("Exactly one search call", 1, searchCallCount[0]);
        assertEquals("Query is wildcard segment", "RtSuggestionCap_*.txt", searchArgsQuery[0]);
        assertNull("projectName is null", searchArgsProject[0]);
        assertEquals("Limit is MAX_SUGGESTIONS + 1", 4, searchArgsLimit[0]);
        assertEquals("Four raw results", 4, rawPaths.size());

        var expectedRawSet = Set.of(
                "/" + PeonTestFixture.PROJECT_NAME + "/RtSuggestionCap_0.txt",
                "/" + PeonTestFixture.PROJECT_NAME + "/RtSuggestionCap_1.txt",
                "/" + PeonTestFixture.PROJECT_NAME + "/RtSuggestionCap_2.txt",
                "/" + PeonTestFixture.PROJECT_NAME + "/RtSuggestionCap_3.txt");
        assertEquals("Raw paths match fixture set", expectedRawSet, Set.copyOf(rawPaths));

        var expected = expectedMiss + sep + "Did you mean:"
                + sep + rawPaths.get(0)
                + sep + rawPaths.get(1)
                + sep + rawPaths.get(2)
                + sep + "more matches — use eclipseSearchFiles";
        assertEquals("Exact miss with first three suggestions and disclosure", expected, result);
    }

    @Test
    public void readMissReportsSuggestionSearchFailure() {
        var tool = new EclipseWorkspaceReadFileTool() {
            @Override
            protected SearchResult searchFiles(String query, String projectName, int limit) {
                throw new IllegalStateException("suggestion search blocked");
            }
        };
        tool.setCurrentProject(project);

        var sep = System.lineSeparator();
        var missPath = "/" + PeonTestFixture.PROJECT_NAME + "/rt-nope/AnyFile.txt";
        var expectedMiss = "No eclipse file found for '" + missPath + "'." + EclipseUtil.unresolvedHint(missPath);
        assertEquals(expectedMiss + sep + "Did you mean: suggestion search failed: suggestion search blocked",
                tool.eclipseReadFile(missPath, null, null));
    }

    @Test
    public void readMissPropagatesSuggestionSearchCancellation() {
        var projectName = PeonTestFixture.PROJECT_NAME;
        var missPath = "/" + projectName + "/rt-nope/AnyFile.txt";

        Object[][] inputs = {
            { new java.util.concurrent.CancellationException("JDK direct"), RuntimeException.class },
            { new RuntimeException("JDK wrapped", new java.util.concurrent.CancellationException("JDK inner")), RuntimeException.class },
            { new OperationCanceledException("Eclipse direct"), RuntimeException.class },
            { new RuntimeException("Eclipse wrapped", new OperationCanceledException("Eclipse inner")), RuntimeException.class },
        };

        for (var input : inputs) {
            var ex = (RuntimeException) input[0];
            var expectedType = (Class<? extends RuntimeException>) input[1];
            var tool = new EclipseWorkspaceReadFileTool() {
                @Override
                protected SearchResult searchFiles(String query, String projectName, int limit) {
                    throw ex;
                }
            };
            tool.setCurrentProject(project);

            var thrown = assertThrows(expectedType,
                    () -> tool.eclipseReadFile(missPath, null, null));
            assertSame(ex, thrown);
        }
    }

    @Test
    public void readMissPropagatesCancellationFromSuggestionRefresh() {
        var refreshCalls = new int[1];
        var refreshed = new ArrayList<IProject>();
        var refreshMonitor = new NullProgressMonitor();
        var capturedCanceled = new OperationCanceledException[1];

        var tool = new EclipseWorkspaceReadFileTool() {
            @Override
            protected IProgressMonitor getProgressMonitor() {
                return refreshMonitor;
            }
            @Override
            protected void refreshScope(List<IProject> scope) {
                refreshCalls[0]++;
                refreshed.addAll(scope);
                refreshMonitor.setCanceled(true);
                try {
                    super.refreshScope(scope);
                } catch (OperationCanceledException e) {
                    capturedCanceled[0] = e;
                    throw e;
                }
            }
        };
        tool.setCurrentProject(project);

        var missPath = "/" + PeonTestFixture.PROJECT_NAME + "/rt-nope/RtSuggestionMissing_20261116.txt";
        var thrown = assertThrows(OperationCanceledException.class,
                () -> tool.eclipseReadFile(missPath, null, null));
        assertSame(capturedCanceled[0], thrown);
        assertEquals("Exactly one refresh", 1, refreshCalls[0]);
        assertEquals("Refreshed only selected project", List.of(project), refreshed);
    }

    @Test
    public void readMissBuildsDiskOnlyHintBeforeSuggestionRefresh() throws Exception {
        var refreshCalls = new int[1];
        var refreshed = new ArrayList<IProject>();
        var tool = new EclipseWorkspaceReadFileTool() {
            @Override
            protected void refreshScope(List<IProject> scope) {
                refreshCalls[0]++;
                refreshed.addAll(scope);
                super.refreshScope(scope);
            }
        };
        tool.setCurrentProject(project);

        var name = "RtSuggestionDiskOnly.txt";
        var diskPath = project.getLocation().toFile().toPath().resolve(name);

        try {
            Files.writeString(diskPath, "rtSuggestionDiskOnlyMustNotBeRead");
            assertFalse("File must be stale in workspace", project.getFile(name).exists());

            var sep = System.lineSeparator();
            var missPath = "/" + PeonTestFixture.PROJECT_NAME + "/" + name;
            var expectedMiss = "No eclipse file found for '" + missPath + "'." + EclipseUtil.unresolvedHint(missPath);
            String result = tool.eclipseReadFile(missPath, null, null);

            assertTrue("Expected miss prefix with disk hint: " + result, result.startsWith(expectedMiss));
            assertTrue("Hint must mention disk existence",
                    result.contains("exists on disk") && result.contains("eclipseRefreshProject"));
            int hintEnd = result.indexOf("eclipseRefreshProject") + "eclipseRefreshProject".length();
            int suggestionStart = result.indexOf("Did you mean:");
            assertTrue("Hint must appear before suggestions",
                    hintEnd < suggestionStart);
            var expectedSuggestion = sep + "Did you mean:" + sep
                    + "/" + PeonTestFixture.PROJECT_NAME + "/" + name;
            assertTrue("Result must end with suggestion: " + result, result.endsWith(expectedSuggestion));
            assertFalse("Must not contain file content", result.contains("rtSuggestionDiskOnlyMustNotBeRead"));
            assertEquals("Exactly one refresh", 1, refreshCalls[0]);
            assertEquals("Refreshed only selected project", List.of(project), refreshed);
            assertTrue("Workspace file must exist after refresh", project.getFile(name).exists());
        } finally {
            Files.deleteIfExists(diskPath);
            project.refreshLocal(IResource.DEPTH_ONE, new NullProgressMonitor());
        }
    }

    @Test
    public void readHitDoesNotSearchForSuggestions() {
        var tool = new EclipseWorkspaceReadFileTool() {
            @Override
            protected SearchResult searchFiles(String query, String projectName, int limit) {
                throw new AssertionError("searchFiles must not be called on a hit");
            }
        };

        var content = tool.eclipseReadFile(
                JdtUtil.pathOf(project) + "/data/utf-8-test.txt", null, null);

        assertEquals("   1: äüß Ö ⚡\n", content);
    }
}