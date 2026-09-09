package org.sterl.llmpeon.scaffold;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.prompt.PromptLoader;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.tool.DynamicRootsWriteValidator;
import org.sterl.llmpeon.tool.SmartTool;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.WriteValidator;
import org.sterl.llmpeon.tool.tools.DiskFileReadTool;
import org.sterl.llmpeon.tool.tools.DiskFileWriteTool;
import org.sterl.llmpeon.tool.tools.DiskGrepTool;
import org.sterl.llmpeon.tool.tools.WebFetchTool;

import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Built-in agent for creating, editing, and managing Peon configuration
 * artifacts (agents, skills, commands). Has config-scoped disk tools and a
 * reload tool.
 */
public class AiScaffoldAgent extends org.sterl.llmpeon.agent.AbstractAgent {

    public static final String NAME = "Peon-Scaffold";

    private static final String BASE_PROMPT = PromptLoader
            .loadWithDefault("scaffold-agent.txt");
    
    private final DiskFileReadTool diskFileReadTool;
    private final DiskFileWriteTool diskFileWriteTool;
    private final DiskGrepTool diskGrepTool;
    /** ADR-0043: additional write roots — the {@code .agents/skills} dirs of the open projects, read at validate time. */
    private volatile Supplier<List<Path>> projectSkillsRoots = List::of;

    public AiScaffoldAgent(ConfiguredChatModel configuredModel) {
        this(configuredModel, null);
    }

    /**
     * @param skillService when non-null, every successful disk write deterministically
     *        refreshes the skill view (R15) — no LLM-driven reload needed. The write has
     *        already succeeded at that point, so a refresh failure is rethrown to the LLM
     *        (tool honesty: no silent stale view).
     */
    public AiScaffoldAgent(ConfiguredChatModel configuredModel, @Nullable SkillService skillService) {
        super(configuredModel, new ToolService(false));

        var configDir = configuredModel.getConfig().getConfigDir();
        diskFileReadTool = new DiskFileReadTool(configDir);
        diskFileWriteTool = new DiskFileWriteTool(configDir);
        diskGrepTool = new DiskGrepTool(configDir);

        if (skillService != null) {
            diskFileWriteTool.setAfterWrite(() -> {
                try {
                    skillService.refreshAll();
                } catch (IOException e) {
                    throw new RuntimeException("Write succeeded but skill refresh failed: " + e.getMessage(), e);
                }
            });
        }

        toolService.addTool(diskFileReadTool);
        toolService.addTool(diskFileWriteTool);
        toolService.addTool(diskGrepTool);
        toolService.addTool(new WebFetchTool());
    }

    @Override
    public ChatResponse call(String message, AiMonitor monitor) {
        var configDir = configuredModel.getConfig().getConfigDir();
        diskFileReadTool.setWorkingDir(configDir);
        diskFileWriteTool.setWorkingDir(configDir);
        diskGrepTool.setWorkingDir(configDir);
        return super.call(message, monitor);
    }

    public void addTool(SmartTool toolObject) {
        toolService.addTool(toolObject);
    }

    /**
     * ADR-0043: supplier for the additional write roots — the {@code .agents/skills} dir of
     * every open project (read at validate time, R10). Default: empty → writes restricted to
     * the config dir.
     */
    public void setProjectSkillsRootsSupplier(Supplier<List<Path>> supplier) {
        this.projectSkillsRoots = supplier == null ? List::of : supplier;
    }

    /** R10/R12: hard write gate — config dir + the open projects' skill dirs. */
    @Override
    public WriteValidator getWriteValidator() {
        return new DynamicRootsWriteValidator(configuredModel.getConfig().getConfigDir(), projectSkillsRoots);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getSystemPrompt() {
        return BASE_PROMPT;
    }

    @Override
    public org.sterl.llmpeon.ai.AgentConfig getConfig() {
        return configuredModel.getConfig().devAgentConfig();
    }
}
