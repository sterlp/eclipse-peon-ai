package org.sterl.llmpeon.scaffold;

import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.prompt.PromptLoader;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.SmartTool;
import org.sterl.llmpeon.tool.ToolService;
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

    public AiScaffoldAgent(ConfiguredChatModel configuredModel) {
        super(configuredModel, new ToolService(false));

        var configDir = configuredModel.getConfig().getConfigDir();
        diskFileReadTool = new DiskFileReadTool(configDir);
        diskFileWriteTool = new DiskFileWriteTool(configDir);
        diskGrepTool = new DiskGrepTool(configDir);
        
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

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getSystemPrompt() {
        return BASE_PROMPT;
    }

    @Override
    public Double getTemperature() {
        return configuredModel.getConfig().getDevTemperature();
    }

    @Override
    public org.sterl.llmpeon.ai.AgentConfig getConfig() {
        return configuredModel.getConfig().devAgentConfig();
    }
}
