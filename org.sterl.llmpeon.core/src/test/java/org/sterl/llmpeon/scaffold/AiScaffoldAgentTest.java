package org.sterl.llmpeon.scaffold;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
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
import org.sterl.llmpeon.tool.tools.DiskFileReadTool;
import org.sterl.llmpeon.tool.tools.DiskFileWriteTool;
import org.sterl.llmpeon.tool.tools.DiskGrepTool;
import org.sterl.llmpeon.tool.tools.SkillTool;
import org.sterl.llmpeon.tool.tools.WebFetchTool;

class AiScaffoldAgentTest extends AbstractMemoryFileTest {

    private static final String CONFIG_DIR = ".peon";

    private ConfiguredChatModel chatModel;
    private LlmConfig config;
    private AgentService agentService;
    private SkillService skillService;
    private CommandService commandService;
    private ToolService sharedToolService;
    private AiScaffoldAgent scaffoldAgent;

    @BeforeEach
    void before() throws Exception {
        tmp = fs.getPath("/" + UUID.randomUUID());
        Files.createDirectory(tmp);
        var configDir = Files.createDirectories(tmp.resolve(CONFIG_DIR));
        config = LlmConfig.builder().configDir(configDir).model("test").build();

        chatModel = Mockito.mock(ConfiguredChatModel.class);
        Mockito.when(chatModel.getConfig()).thenReturn(config);

        sharedToolService = new ToolService();
        skillService = new SkillService();
        commandService = new CommandService();

        agentService = new AgentService(true,
                configDir.resolve(LlmConfig.AGENT_DIRECTORY),
                sharedToolService, chatModel);

        // Create scaffold agent with its own ToolService (as PeonAiService does)

        scaffoldAgent = new AiScaffoldAgent(chatModel);
        scaffoldAgent.addTool(new SkillTool(skillService));
        scaffoldAgent.addTool(new ReloadConfigTool(agentService, skillService, commandService, config));
        agentService.addPersistentAgent(scaffoldAgent);
    }

    @Test
    void hasCorrectName() {
        assertThat(scaffoldAgent.getName()).isEqualTo("Peon-Scaffold");
    }

    @Test
    void isRegisteredAsPersistentAgent() {
        assertThat(agentService.isPersistentAgent("Peon-Scaffold")).isTrue();
    }

    @Test
    void survivesClearAgents() {
        // GIVEN — scaffold is in the agents list
        assertThat(agentService.getAgents()).anyMatch(a -> a.getName().equals("Peon-Scaffold"));

        // WHEN — clearAgents is called (via reloadAgents with no agent directory)
        agentService.reloadAgents();

        // THEN — scaffold is still present
        assertThat(agentService.getAgents()).anyMatch(a -> a.getName().equals("Peon-Scaffold"));
    }

    @Test
    void hasOwnToolServiceWithCorrectTools() {
        var toolService = scaffoldAgent.getToolService();
        assertThat(toolService).isNotNull();

        // THEN — has config-scoped disk tools
        assertThat(toolService.getTool(DiskFileReadTool.class)).isPresent();
        assertThat(toolService.getTool(DiskFileWriteTool.class)).isPresent();
        assertThat(toolService.getTool(DiskGrepTool.class)).isPresent();

        // THEN — has skill tool
        assertThat(toolService.getTool(SkillTool.class)).isPresent();

        // THEN — has web fetch tool
        assertThat(toolService.getTool(WebFetchTool.class)).isPresent();

        // THEN — has reload config tool
        assertThat(toolService.getTool(ReloadConfigTool.class)).isPresent();

        // THEN — no shell tool (not in scaffold's ToolService)
        var executors = toolService.getExecutors();
        assertThat(executors).noneMatch(e -> e.getSpec().name().equals("shellCommand"));
    }

    @Test
    void diskToolsAreConfigScoped() {
        var readTool = scaffoldAgent.getToolService().getTool(DiskFileReadTool.class).orElseThrow();
        assertThat(readTool.getWorkingDir()).isEqualTo(tmp.resolve(CONFIG_DIR));

        var writeTool = scaffoldAgent.getToolService().getTool(DiskFileWriteTool.class).orElseThrow();
        assertThat(writeTool.getWorkingDir()).isEqualTo(tmp.resolve(CONFIG_DIR));
    }

    @Test
    void usesDevAgentConfig() {
        assertThat(scaffoldAgent.getConfig().getModel()).isEqualTo("test");
        assertThat(scaffoldAgent.getTemperature()).isEqualTo(config.getDevTemperature());
    }

    @Test
    void reloadConfigToolExists() {
        var reloadTool = scaffoldAgent.getToolService().getTool(ReloadConfigTool.class).orElseThrow();
        assertThat(reloadTool).isNotNull();
    }

    @Test
    void isNotReadOnly() {
        assertThat(scaffoldAgent.isReadOnly()).isFalse();
    }
}
