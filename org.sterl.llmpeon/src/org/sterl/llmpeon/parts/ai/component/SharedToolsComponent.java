package org.sterl.llmpeon.parts.ai.component;

import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.command.CommandService;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.tools.AskUserTool;
import org.sterl.llmpeon.parts.tools.EclipseBuildTool;
import org.sterl.llmpeon.parts.tools.EclipseCodeNavigationTool;
import org.sterl.llmpeon.parts.tools.EclipseConsoleLogTool;
import org.sterl.llmpeon.parts.tools.EclipseGrepTool;
import org.sterl.llmpeon.parts.tools.EclipseRunTestTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceReadFileTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceWriteFileTool;
import org.sterl.llmpeon.parts.tools.debug.JavaDebugTool;
import org.sterl.llmpeon.parts.tools.memory.WorkspaceMemoryTool;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.docslinter.DocsIdTool;
import org.sterl.llmpeon.docslinter.DocsLinterTool;
import org.sterl.llmpeon.tool.tools.DiskFileReadTool;
import org.sterl.llmpeon.tool.tools.DiskFileWriteTool;
import org.sterl.llmpeon.tool.tools.DiskGrepTool;
import org.sterl.llmpeon.tool.tools.SearchAgentTool;
import org.sterl.llmpeon.tool.tools.WebGetTool;
import org.sterl.llmpeon.tool.tools.SkillTool;

/**
 * Builds and owns the shared {@link ToolService}: every Eclipse tool, the WorkspaceMemoryTool,
 * the SearchAgentTool privilege filter and the disk-tool toggle. No service dependency —
 * the Service pulls what it needs via the getters.
 */
public class SharedToolsComponent {

    private final ToolService sharedToolService = new ToolService();

    private final EclipseWorkspaceWriteFileTool workspaceWriteFilesTool = new EclipseWorkspaceWriteFileTool();
    private final EclipseWorkspaceReadFileTool workspaceReadFilesTool = new EclipseWorkspaceReadFileTool();
    private final EclipseGrepTool eclipseGrepTool = new EclipseGrepTool();
    private final JavaDebugTool javaDebugTool = new JavaDebugTool();

    private final DiskFileWriteTool diskFileWriteTool;
    private final DiskFileReadTool diskFileReadTool;
    private final DiskGrepTool diskGrepTool;
    private final DocsLinterTool docsLinterTool;
    private final DocsIdTool docsIdTool;

    /** webGet (R-WEB-8): same risk class as the disk write tools — gated behind diskToolsEnabled, default OFF. */
    private final WebGetTool webGetTool = new WebGetTool();

    public SharedToolsComponent(SkillService skillService, CommandService commandService) {
        // filter eclipse tools from the search agents ...
        var sa = sharedToolService.getTool(SearchAgentTool.class).get();
        sa.setFilter(sa.getFilter().and(e -> !(e.getTool() instanceof AskUserTool)
                       && !(e.getTool() instanceof WorkspaceMemoryTool)));

        sharedToolService.addTool(new SkillTool(skillService));
        sharedToolService.addTool(workspaceWriteFilesTool);
        sharedToolService.addTool(workspaceReadFilesTool);

        var rootPath = EclipseUtil.workspacePath();
        diskFileWriteTool = new DiskFileWriteTool(rootPath);
        diskFileReadTool  = new DiskFileReadTool(rootPath);
        diskGrepTool      = new DiskGrepTool(rootPath);
        docsLinterTool    = new DocsLinterTool(rootPath);
        docsIdTool        = new DocsIdTool(docsLinterTool);

        sharedToolService.addTool(docsLinterTool);
        sharedToolService.addTool(new WorkspaceMemoryTool());
        sharedToolService.addTool(new EclipseBuildTool());
        sharedToolService.addTool(javaDebugTool);
        sharedToolService.addTool(eclipseGrepTool);
        sharedToolService.addTool(new EclipseRunTestTool());
        sharedToolService.addTool(new EclipseCodeNavigationTool());
        sharedToolService.addTool(new EclipseConsoleLogTool());
    }

    /** Adds or removes the disk tools depending on {@code config.isDiskToolsEnabled()}. */
    public void updateActiveDiskTools(LlmConfig config) {
        if (config.isDiskToolsEnabled()) {
            if (sharedToolService.getTool(DiskFileWriteTool.class).isEmpty()) {
                sharedToolService.addTool(diskFileWriteTool);
                sharedToolService.addTool(diskFileReadTool);
                sharedToolService.addTool(diskGrepTool);
                sharedToolService.addTool(webGetTool);
            }
        } else {
            if (sharedToolService.getTool(DiskFileWriteTool.class).isPresent()) {
                sharedToolService.removeTool(diskFileWriteTool);
                sharedToolService.removeTool(diskFileReadTool);
                sharedToolService.removeTool(diskGrepTool);
                sharedToolService.removeTool(webGetTool);
            }
        }
    }

    public ToolService toolService() {
        return sharedToolService;
    }

    public WorkspaceMemoryTool workspaceMemoryTool() {
        return sharedToolService.getTool(WorkspaceMemoryTool.class).get();
    }

    public EclipseWorkspaceWriteFileTool workspaceWriteFilesTool() {
        return workspaceWriteFilesTool;
    }

    public EclipseWorkspaceReadFileTool workspaceReadFilesTool() {
        return workspaceReadFilesTool;
    }

    public EclipseGrepTool eclipseGrepTool() {
        return eclipseGrepTool;
    }

    public JavaDebugTool javaDebugTool() {
        return javaDebugTool;
    }

    public DiskFileWriteTool diskFileWriteTool() {
        return diskFileWriteTool;
    }

    public DiskFileReadTool diskFileReadTool() {
        return diskFileReadTool;
    }

    public DiskGrepTool diskGrepTool() {
        return diskGrepTool;
    }

    public WebGetTool webGetTool() {
        return webGetTool;
    }

    public DocsLinterTool docsLinterTool() {
        return docsLinterTool;
    }

    public DocsIdTool docsIdTool() {
        return docsIdTool;
    }
}
