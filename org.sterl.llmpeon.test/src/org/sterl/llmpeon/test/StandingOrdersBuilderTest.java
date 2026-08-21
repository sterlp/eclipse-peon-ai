package org.sterl.llmpeon.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.internal.core.util.SimpleDocument;
import org.eclipse.jface.text.TextSelection;
import org.junit.Before;
import org.junit.Test;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.context.SimpleContextItem;
import org.sterl.llmpeon.parts.PeonAiService;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.scaffold.AiScaffoldAgent;

public class StandingOrdersBuilderTest extends AbstractIntegrationTest {
    PeonAiService aiService;
    
    private StreamMock streamMock = new StreamMock();

    @Before
    public void beforeEach() {
        var ccm = new ConfiguredChatModel(LlmConfig.builder()
                .model("test")
                .url("http://localhost:0")
                .configDir(Path.of(System.getProperty("java.io.tmpdir"), ".peon-test"))
                .build()
        );
        aiService = new PeonAiService(() -> {}, null, null, null, ccm);
        aiService.setProject(project);
        aiService.clearAll();
        ccm.setChatModel(streamMock.buildOkMock());

        aiService.setProject(project);
    }

    @Test
    public void test_AgentsMdService() {
        // GIVEN
        eclipseWriteFile("AGENTS.md", "(Global Rules)");
        
        // WHEN
        aiService.call("Hallo Paul", null);

        // THAN agents md
        assertHasMessageWith(streamMock.getLastUserMessagesAsString(), "/AGENTS.md");
        assertHasMessageWith(streamMock.getLastUserMessagesAsString(), "(Global Rules)");
        // AND no nulls ... 
        assertHasNoMessageWith(streamMock.getLastUserMessagesAsString(), " null");
    }
    
    @Test
    public void test_user_context() {
        // GIVEN
        
        // WHEN
        aiService.call("Hallo Paul", null);
        assertHasMessageWith(streamMock.getLastUserMessagesAsString(), project.getName());

        aiService.call("Hallo Paul", null);

        // THEN
        var messages = streamMock.getLastRequest().messages();
        assertHasUserMessageWith(messages, project.getName());
        assertHasUserMessageWith(messages, JdtUtil.diskPathOf(project));

        // AND no nulls ... 
        assertHasNoUserMessageWith(messages, "null");
        
        // AND
        assertTrue(streamMock.count("Hallo Paul") == 2);

        assertTrue("Expected " + JdtUtil.diskPathOf(project) + " only once: " + System.lineSeparator() +
                String.join(System.lineSeparator(), streamMock.getLastUserMessagesAsString()), 
                streamMock.count(JdtUtil.diskPathOf(project)) == 1); 
    }
    
    @Test
    public void test_one_time_order_flows_through_and_is_consumed() {
        // GIVEN — a command/skill body added as a one-time order
        aiService.getUserContext().addOneTimeOrder(new SimpleContextItem("Review the code and report any issues."));

        // WHEN
        aiService.call("Hallo Paul", null);

        // THEN — the one-time order is part of the built standing orders
        assertHasMessageWith(streamMock.getLastUserMessagesAsString(), "Review the code and report any issues.");
        
        // AND
        aiService.clear();
        aiService.call("Hallo Paul", null);
        assertHasNoMessageWith(streamMock.getLastUserMessagesAsString(), "Review the code and report any issues.");
    }


    @Test
    public void test_file_selection_with_text_range() {
        // GIVEN
        var pomResource = project.findMember("pom.xml");
        assertNotNull(pomResource);
        EclipseUtil.openInEditor((IFile)pomResource);
        // AND
        var doc = new SimpleDocument("Hallo von Paul - das sollten wir nicht sehen");
        var mockTextSelection = new TextSelection(doc, 0, doc.getLength());
        aiService.getUserContext().setTextSelection(mockTextSelection);
        aiService.getUserContext().setSelectedResource(pomResource);

        // WHEN
        aiService.call("Hallo Paul", null);

        // THEN - should contain path to pom.xml
        assertHasMessageWith(streamMock.getLastUserMessagesAsString(), "pom.xml");

        // AND start marker <project should be present (line 1)
        assertHasMessageWith(streamMock.getLastUserMessagesAsString(), "<project");

        // AND end marker </project> should be present
        assertHasMessageWith(streamMock.getLastUserMessagesAsString(), "</project>");

        // AND
        assertHasNoMessageWith(streamMock.getLastUserMessagesAsString(), "das sollten wir nicht sehen");
    }

    // ---------------------------------------------------------------------
    // Agent-specific AGENTS-<agent>.md tests
    // ---------------------------------------------------------------------


    @Test
    public void test_agentsMd_and_agentSpecificMd_both_loaded() {
        // GIVEN a project with AGENTS.md and AGENTS-DEV.md
        eclipseWriteFile("AGENTS.md", "Test Specifics");
        eclipseWriteFile("AGENTS-DEV.md", "Dev agent content");

        // WHEN standing orders are built
        assertTrue(aiService.setActiveAgent("Peon-Dev"));
        aiService.call("Hallo Paul", null);

        // THEN both AGENTS.md and AGENTS-DEV.md content is included
        assertHasMessageWith(streamMock.getLastUserMessagesAsString(), "AGENTS.md");
        assertHasMessageWith(streamMock.getLastUserMessagesAsString(), "Test Specifics");
        assertHasMessageWith(streamMock.getLastUserMessagesAsString(), "AGENTS-DEV.md");
        assertHasMessageWith(streamMock.getLastUserMessagesAsString(), "Dev agent content");
    }

    @Test
    public void test_agentsMd_only_no_agentSpecific() {
        // GIVEN a project with only AGENTS.md (no AGENTS-DEV.md)
        eclipseWriteFile("AGENTS.md", "Test Specifics");
        eclipseDeleteResource("AGENTS-DEV.md");

        // WHEN standing orders are built
        assertTrue(aiService.setActiveAgent("Peon-Dev"));
        aiService.call("Hallo Paul", null);

        // THEN only AGENTS.md content is included
        var messages = streamMock.getLastUserMessagesAsString();
        assertHasMessageWith(messages, "AGENTS.md");
        assertHasMessageWith(messages, "Test Specifics");
        assertHasNoMessageWith(messages, "Dev agent content");
    }

    @Test
    public void test_agentSpecificMd_only_no_base() {
        // GIVEN a project with only AGENTS-PLAN.md (no AGENTS.md)
        eclipseWriteFile("AGENTS-PLAN.md", "Plan agent content");
        // AND Peon-Plan is the active agent
        eclipseDeleteResource("AGENTS.md");

        // WHEN standing orders are built
        assertTrue(aiService.setActiveAgent(AiPlanAgent.NAME));
        aiService.call("Hallo Paul", null);

        // THEN only AGENTS-PLAN.md content is included
        var messages = streamMock.getLastRequest().messages();
        assertHasUserMessageWith(messages, "AGENTS-PLAN.md");
        assertHasUserMessageWith(messages, "Plan agent content");
    }

    @Test
    public void test_agent_switch_changes_agentSpecificMd() {
        // GIVEN
        eclipseWriteFile("AGENTS.md", "Test Specifics");
        eclipseWriteFile("AGENTS-PLAN.md", "Plan agent content");
        eclipseWriteFile("AGENTS-DEV.md", "Dev agent content");
        
        // AND Peon-Dev is the active agent
        assertTrue(aiService.setActiveAgent("Peon-Dev"));

        // WHEN standing orders are built with Peon-Dev
        aiService.call("Hallo Paul", null);

        // THEN AGENTS-DEV.md content is included
        var messages = streamMock.getLastRequest().messages();
        assertHasUserMessageWith(messages, "AGENTS-DEV.md");
        assertHasUserMessageWith(messages, "Dev agent content");

        // WHEN the agent is switched to Peon-Plan
        assertTrue(aiService.setActiveAgent("Peon-Plan"));
        aiService.call("Hallo Paul", null);

        // THEN AGENTS-PLAN.md content is included instead
        messages = streamMock.getLastRequest().messages();
        assertHasUserMessageWith(messages, "AGENTS-PLAN.md");
        assertHasUserMessageWith(messages, "Plan agent content");
        assertHasNoUserMessageWith(messages, "Dev agent content");
    }

    @Test
    public void test_agentSpecificMd_caseInsensitive_fallback() throws CoreException {
        // GIVEN
        eclipseWriteFile("AGENTS.md", "Test Specifics");
        eclipseWriteFile("agents-dev.md", "Lowercase dev content");

        // AND Peon-Dev is the active agent
        assertTrue(aiService.setActiveAgent("Peon-Dev"));

        // WHEN standing orders are built
        aiService.call("Hallo Paul", null);

        // THEN both AGENTS.md and agents-dev.md content is included
        var messages = streamMock.getLastRequest().messages();
        assertHasUserMessageWith(messages, "AGENTS.md");
        assertHasUserMessageWith(messages, "Test Specifics");
        assertHasUserMessageWith(messages, "agents-dev.md");
        assertHasUserMessageWith(messages, "Lowercase dev content");
    }

    @Test
    public void test_customAgent_specificMd_loaded() throws CoreException {
        // GIVEN a project with AGENTS.md and AGENTS-Docs-Assistant.md
        eclipseWriteFile("AGENTS.md", "Test Specifics");
        eclipseWriteFile("AGENTS-Docs-Assistant.md", "Docs assistant content");

        // AND a custom agent "Docs-Assistant" is active
        aiService.setProject(project);

        // WHEN standing orders are built
        assertTrue(aiService.setActiveAgent(AiScaffoldAgent.NAME));
        aiService.call("Hallo Paul", null);

        // THEN both AGENTS.md and AGENTS-Docs-Assistant.md content is included
        var messages = streamMock.getLastRequest().messages();
        assertHasNoUserMessageWith(messages, "Test Specifics");
        assertHasNoUserMessageWith(messages, "Docs assistant content");
    }
}
