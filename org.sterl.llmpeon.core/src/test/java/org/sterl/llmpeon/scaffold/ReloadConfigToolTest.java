package org.sterl.llmpeon.scaffold;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.sterl.llmpeon.AbstractMemoryFileTest;
import org.sterl.llmpeon.AgentService;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.command.CommandService;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.tool.ToolService;

class ReloadConfigToolTest extends AbstractMemoryFileTest {

    private static final String CONFIG_DIR = ".peon";

    private ConfiguredChatModel chatModel;
    private LlmConfig config;
    private AgentService agentService;
    private SkillService skillService;
    private CommandService commandService;
    private ReloadConfigTool tool;

    @BeforeEach
    void before() throws Exception {
        tmp = fs.getPath("/" + UUID.randomUUID());
        Files.createDirectory(tmp);
        var configDir = Files.createDirectories(tmp.resolve(CONFIG_DIR));
        config = LlmConfig.builder().configDir(configDir).model("test").build();

        chatModel = Mockito.mock(ConfiguredChatModel.class);
        Mockito.when(chatModel.getConfig()).thenReturn(config);

        skillService = new SkillService();
        commandService = new CommandService();
        agentService = new AgentService(true,
                configDir.resolve(LlmConfig.AGENT_DIRECTORY),
                new ToolService(), chatModel);

        tool = new ReloadConfigTool(agentService, skillService, commandService, config, null);
    }

    @Test
    void reloadConfigTriggersAllServices() throws Exception {
        // GIVEN
        var result = tool.reloadConfig();

        // THEN
        assertThat(result).contains("Reloaded config from");
        assertThat(result).contains("Agents:");
        assertThat(result).contains("Skills:");
        assertThat(result).contains("Commands:");
    }

    @Test
    void reloadConfigReportsCounts() throws Exception {
        // GIVEN
        var result = tool.reloadConfig();

        // THEN
        assertThat(result).contains("Agents: 2 loaded");
    }

    @Test
    void reloadConfigReturnsErrorWhenConfigDirIsNull() throws Exception {
        // GIVEN — no config directory
        var noDirConfig = LlmConfig.builder().configDir(null).build();
        var noDirTool = new ReloadConfigTool(agentService, skillService, commandService, noDirConfig, null);

        // WHEN
        var result = noDirTool.reloadConfig();

        // THEN
        assertThat(result).contains("Error: config directory is not set");
    }

    @Test
    void onReloadFiresAfterAllServicesSucceed() throws Exception {
        // GIVEN — track invocation order
        List<String> order = new ArrayList<>();
        var mockAgentService = Mockito.mock(AgentService.class);
        Mockito.when(mockAgentService.reloadAgents()).thenAnswer(inv -> {
            order.add("agents");
            return true;
        });
        var mockSkillService = Mockito.mock(SkillService.class);
        Mockito.when(mockSkillService.refresh(Mockito.any(Path.class))).thenAnswer(inv -> {
            order.add("skills");
            return true;
        });
        var mockCommandService = Mockito.mock(CommandService.class);
        Mockito.when(mockCommandService.refresh(Mockito.any(Path.class))).thenAnswer(inv -> {
            order.add("commands");
            return true;
        });

        var tool = new ReloadConfigTool(mockAgentService, mockSkillService, mockCommandService, config, () -> {
            order.add("onReload");
        });

        // WHEN
        tool.reloadConfig();

        // THEN — callback fires AFTER all three services
        assertThat(order).containsExactly("agents", "skills", "commands", "onReload");
    }

    @Test
    void onReloadDoesNotFireWhenSkillServiceFails() throws Exception {
        // GIVEN — skill service throws IOException
        var mockAgentService = Mockito.mock(AgentService.class);
        Mockito.when(mockAgentService.reloadAgents()).thenReturn(true);
        var mockSkillService = Mockito.mock(SkillService.class);
        Mockito.when(mockSkillService.refresh(Mockito.any(Path.class))).thenThrow(new java.io.IOException("disk error"));
        var mockCommandService = Mockito.mock(CommandService.class);
        boolean[] callbackFired = {false};

        var tool = new ReloadConfigTool(mockAgentService, mockSkillService, mockCommandService, config, () -> {
            callbackFired[0] = true;
        });

        // WHEN + THEN
        assertThatThrownBy(() -> tool.reloadConfig()).isInstanceOf(java.io.IOException.class);
        assertThat(callbackFired[0]).isFalse(); // callback must NOT fire
    }
}
