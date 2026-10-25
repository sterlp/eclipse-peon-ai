package org.sterl.llmpeon.parts.ai.component;

import java.util.ArrayList;
import java.util.List;

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
import org.sterl.llmpeon.parts.tools.PlanTool;
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
import org.sterl.llmpeon.tool.tools.SkillTool;
import org.sterl.llmpeon.tool.web.WebGetTool;

/**
 * Builds and owns the shared {@link ToolService}: every Eclipse tool, the WorkspaceMemoryTool,
 * the SearchAgentTool privilege filter and the disk-tool toggle. No service dependency —
 * the Service pulls what it needs via the getters.
 */
public class SharedToolsComponent {

    private final ToolService sharedToolService = new ToolService();

    /** ADR-0067: every agent-facing registry that follows the disk-tool gate (shared + registered agents). */
    private final List<ToolService> diskGatedRegistries = new ArrayList<>();

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
        diskGatedRegistries.add(sharedToolService);

        // filter eclipse tools from the search agents ...
        var sa = sharedToolService.getTool(SearchAgentTool.class).get();
        // R-TF-2: PlanTool reports isEditTool()==false but really writes peon-plan/overview.md —
        // a read-only research agent must not see it, so exclude it by type.
        sa.setFilter(sa.getFilter().and(e -> !(e.getTool() instanceof AskUserTool)
                       && !(e.getTool() instanceof WorkspaceMemoryTool)
                       && !(e.getTool() instanceof PlanTool)));

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

    /**
     * Registers an additional agent-facing registry (ADR-0067, e.g. the Peon-PO's own tool
     * service) that follows the disk-tool gate like the shared service.
     */
    public void registerDiskGatedRegistry(ToolService registry) {
        diskGatedRegistries.add(registry);
    }

    /**
     * Adds or removes the disk tools depending on {@code config.isDiskToolsEnabled()} — on every
     * disk-gated registry (the shared service plus every registered agent registry, ADR-0067).
     * The same four instances are toggled atomically per registry.
     */
    public void updateActiveDiskTools(LlmConfig config) {
        for (ToolService registry : diskGatedRegistries) {
            if (config.isDiskToolsEnabled()) {
                if (registry.getTool(DiskFileWriteTool.class).isEmpty()) {
                    registry.addTool(diskFileWriteTool);
                    registry.addTool(diskFileReadTool);
                    registry.addTool(diskGrepTool);
                    registry.addTool(webGetTool);
                }
            } else {
                if (registry.getTool(DiskFileWriteTool.class).isPresent()) {
                    registry.removeTool(diskFileWriteTool);
                    registry.removeTool(diskFileReadTool);
                    registry.removeTool(diskGrepTool);
                    registry.removeTool(webGetTool);
                }
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
