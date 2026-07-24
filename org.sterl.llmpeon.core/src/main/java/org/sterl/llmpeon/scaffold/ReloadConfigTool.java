package org.sterl.llmpeon.scaffold;

import java.io.IOException;

import org.sterl.llmpeon.AgentService;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.command.CommandService;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.tool.tools.AbstractTool;

import dev.langchain4j.agent.tool.Tool;

/**
 * Tool that triggers a refresh on AgentService, SkillService, and CommandService.
 * Used by the scaffold agent to make newly created artifacts immediately available.
 */
public class ReloadConfigTool extends AbstractTool {

    private final AgentService agentService;
    private final SkillService skillService;
    private final CommandService commandService;
    private volatile LlmConfig config;
    private final Runnable onReload;

    public ReloadConfigTool(AgentService agentService,
                            SkillService skillService,
                            CommandService commandService,
                            LlmConfig config,
                            Runnable onReload) {
        this.agentService = agentService;
        this.skillService = skillService;
        this.commandService = commandService;
        this.config = config;
        this.onReload = onReload;
    }

    public void updateConfig(LlmConfig config) {
        this.config = config;
    }

    @Tool("Reload all configuration (agents, skills, commands) — call after creating/editing artifacts so they become immediately available.")
    public String reloadConfig() throws IOException {
        var configDir = config.getConfigDir();
        if (configDir == null) {
            return "Error: config directory is not set";
        }

        // Reload agents — pure business logic, no callback
        agentService.reloadAgents();

        // Reload skills
        var skillDir = configDir.resolve(LlmConfig.SKILL_DIRECTORY);
        skillService.refresh(skillDir);

        // Reload commands
        var commandDir = configDir.resolve(LlmConfig.COMMAND_DIRECTORY);
        commandService.refresh(commandDir);

        onTool("Reloaded config from " + configDir);
        String result = "Reloaded config from " + configDir + ":" + System.lineSeparator() +
                "  Agents: " + agentService.loadedAgentCount() + " loaded" + System.lineSeparator() +
                "  Skills: " + skillService.loadedSkillCount() + " loaded" + System.lineSeparator() +
                "  Commands: " + commandService.loadedCommandCount() + " loaded";

        // Fire callback after ALL services succeeded
        if (onReload != null) {
            try { onReload.run(); } catch (Exception e) { /* reload succeeded, callback failure is non-critical */ }
        }
        return result;
    }
}
