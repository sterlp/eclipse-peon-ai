package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.internal.core.util.SimpleDocument;
import org.eclipse.jface.text.TextSelection;
import org.junit.Before;
import org.junit.Test;
import org.sterl.llmpeon.StandingOrdersBuilder;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.parts.PeonAiService;
import org.sterl.llmpeon.parts.agentsmd.AgentsMdService;
import org.sterl.llmpeon.parts.model.UserContext;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.JdtUtil;

public class StandingOrdersBuilderTest extends AbstractIntegrationTest {
    PeonAiService aiService ;
    UserContext userContext;
    StandingOrdersBuilder standingOrders;
    AgentsMdService agentsMdService;

    private Collection<String> render(Collection<ContextItem> items) {
        return items.stream().map(ContextItem::render).collect(Collectors.toList());
    }
    
    @Before
    public void beforeEach() {
        aiService = new PeonAiService(null, null, null, null);
        userContext = new UserContext();
        standingOrders = new StandingOrdersBuilder()
                .add(aiService)
                .add(userContext)
                .add(aiService.getAgentsMdService());
        
        aiService.setProject(project);
        userContext.setCurrentProject(project);
        agentsMdService = aiService.getAgentsMdService();
    }

    @Test
    public void test_AgentsMdService() {
        // GIVEN
        eclipseWriteFile("AGENTS.md", "(Global Rules)");
        
        // WHEN
        agentsMdService.load(project);
        var messages = render(standingOrders.buildItems());

        // THAN agents md
        assertHasMessageWith(messages, "/AGENTS.md");
        assertHasMessageWith(messages, "(Global Rules)");
        // AND no nulls ... 
        assertHasNoMessageWith(messages, " null");
    }
    
    @Test
    public void test_user_context() {
        // GIVEN
        
        // WHEN
        var messages = render(standingOrders.buildItems());

        // THEN
        assertHasMessageWith(messages, project.getName());
        assertHasMessageWith(messages, JdtUtil.diskPathOf(project));

        // AND no nulls ... 
        assertHasNoMessageWith(messages, " null");
    }
    
    @Test
    public void test_one_time_order_flows_through_and_is_consumed() {
        // GIVEN — a command/skill body added as a one-time order
        standingOrders.addOneTimeOrder("Review the code and report any issues.");

        // WHEN
        var messages = render(standingOrders.buildItems());

        // THEN — the one-time order is part of the built standing orders
        assertHasMessageWith(messages, "Review the code and report any issues.");

        // AND — it is consumed: a second build no longer contains it
        var second = render(standingOrders.buildItems());
        assertHasNoMessageWith(second, "Review the code and report any issues.");
    }

    @Test
    public void test_one_time_order_appended_after_providers() {
        // GIVEN
        eclipseWriteFile("AGENTS.md", "Test Specifics");
        standingOrders.addOneTimeOrder("Review the code and report any issues.");

        // WHEN
        agentsMdService.load(project);
        var messages = render(standingOrders.buildItems());

        // THEN — provider content and the command body both present
        assertHasMessageWith(messages, "/AGENTS.md");
        assertHasMessageWith(messages, "Review the code and report any issues.");

        // AND — the one-time order is consumed
        var second = render(standingOrders.buildItems());
        assertHasNoMessageWith(second, "Review the code and report any issues.");
    }

    @Test
    public void test_file_selection_with_text_range() {
        // GIVEN - selected file is pom.xml with text selection lines 1-2
        userContext.setCurrentProject(project);
        // AND
        var pomResource = project.findMember("pom.xml");
        assertNotNull(pomResource);
        EclipseUtil.openInEditor((IFile)pomResource);
        // AND
        var doc = new SimpleDocument("Hallo von Paul - das sollten wir nicht sehen");
        var mockTextSelection = new TextSelection(doc, 0, doc.getLength());
        userContext.setTextSelection(mockTextSelection);
        userContext.setSelectedResource(pomResource);

        // WHEN
        var messages = render(standingOrders.buildItems());

        // THEN - should contain path to pom.xml
        assertHasMessageWith(messages, "pom.xml");

        // AND start marker <project should be present (line 1)
        assertHasMessageWith(messages, "<project");

        // AND end marker </project> should be present
        assertHasMessageWith(messages, "</project>");

        // AND
        assertHasNoMessageWith(messages, "das sollten wir nicht sehen");
    }

    // ---------------------------------------------------------------------
    // Agent-specific AGENTS-<agent>.md tests
    // ---------------------------------------------------------------------


    @Test
    public void test_agentsMd_and_agentSpecificMd_both_loaded() {
        // GIVEN a project with AGENTS.md and AGENTS-DEV.md
        eclipseWriteFile("AGENTS.md", "Test Specifics");
        eclipseWriteFile("AGENTS-DEV.md", "Dev agent content");

        // AND Peon-Dev is the active agent
        agentsMdService.load(project);
        agentsMdService.setAgentNameSupplier(() -> "Peon-Dev");

        // WHEN standing orders are built
        var messages = render(standingOrders.buildItems());

        // THEN both AGENTS.md and AGENTS-DEV.md content is included
        assertHasMessageWith(messages, "AGENTS.md");
        assertHasMessageWith(messages, "Test Specifics");
        assertHasMessageWith(messages, "AGENTS-DEV.md");
        assertHasMessageWith(messages, "Dev agent content");
    }

    @Test
    public void test_agentsMd_only_no_agentSpecific() {
        // GIVEN a project with only AGENTS.md (no AGENTS-DEV.md)
        eclipseWriteFile("AGENTS.md", "Test Specifics");
        eclipseDeleteResource("AGENTS-DEV.md");

        agentsMdService.load(project);
        // AND Peon-Dev is the active agent
        agentsMdService.setAgentNameSupplier(() -> "Peon-Dev");

        // WHEN standing orders are built
        var messages = render(standingOrders.buildItems());

        // THEN only AGENTS.md content is included
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

        agentsMdService.load(project);
        agentsMdService.setAgentNameSupplier(() -> "Peon-Plan");

        // WHEN standing orders are built
        var messages = render(standingOrders.buildItems());

        // THEN only AGENTS-PLAN.md content is included
        assertHasMessageWith(messages, "AGENTS-PLAN.md");
        assertHasMessageWith(messages, "Plan agent content");
    }

    @Test
    public void test_agent_switch_changes_agentSpecificMd() {
        // GIVEN
        eclipseWriteFile("AGENTS.md", "Test Specifics");
        eclipseWriteFile("AGENTS-PLAN.md", "Plan agent content");
        eclipseWriteFile("AGENTS-DEV.md", "Dev agent content");
        
        // AND Peon-Dev is the active agent
        agentsMdService.load(project);
        agentsMdService.setAgentNameSupplier(() -> "Peon-Dev");

        // WHEN standing orders are built with Peon-Dev
        var messages = render(standingOrders.buildItems());

        // THEN AGENTS-DEV.md content is included
        assertHasMessageWith(messages, "AGENTS-DEV.md");
        assertHasMessageWith(messages, "Dev agent content");

        // WHEN the agent is switched to Peon-Plan
        agentsMdService.setAgentNameSupplier(() -> "Peon-Plan");
        messages = render(standingOrders.buildItems());

        // THEN AGENTS-PLAN.md content is included instead
        assertHasMessageWith(messages, "AGENTS-PLAN.md");
        assertHasMessageWith(messages, "Plan agent content");
        assertHasNoMessageWith(messages, "Dev agent content");
    }

    @Test
    public void test_agentSpecificMd_caseInsensitive_fallback() throws CoreException {
        // GIVEN
        eclipseWriteFile("AGENTS.md", "Test Specifics");
        eclipseWriteFile("agents-dev.md", "Lowercase dev content");

        // AND Peon-Dev is the active agent
        agentsMdService.load(project);
        agentsMdService.setAgentNameSupplier(() -> "Peon-Dev");

        // WHEN standing orders are built
        var messages = render(standingOrders.buildItems());

        // THEN both AGENTS.md and agents-dev.md content is included
        assertHasMessageWith(messages, "AGENTS.md");
        assertHasMessageWith(messages, "Test Specifics");
        assertHasMessageWith(messages, "agents-dev.md");
        assertHasMessageWith(messages, "Lowercase dev content");
    }

    @Test
    public void test_customAgent_specificMd_loaded() throws CoreException {
        // GIVEN a project with AGENTS.md and AGENTS-Docs-Assistant.md
        eclipseWriteFile("AGENTS.md", "Test Specifics");
        eclipseWriteFile("AGENTS-Docs-Assistant.md", "Docs assistant content");

        // AND a custom agent "Docs-Assistant" is active
        agentsMdService.load(project);
        agentsMdService.setAgentNameSupplier(() -> "Docs-Assistant");

        // WHEN standing orders are built
        var messages = render(standingOrders.buildItems());

        // THEN both AGENTS.md and AGENTS-Docs-Assistant.md content is included
        assertHasMessageWith(messages, "AGENTS.md");
        assertHasMessageWith(messages, "Test Specifics");
        assertHasMessageWith(messages, "AGENTS-Docs-Assistant.md");
        assertHasMessageWith(messages, "Docs assistant content");
    }

    // ---------------------------------------------------------------------
    // AgentsMdContextItem.itemsFor — 2 items, own label + ADR-0029 header each
    // ---------------------------------------------------------------------

    @Test
    public void test_agentsMd_itemsFor_baseAndAgent_ownLabelAndDedupKeyEach() {
        // GIVEN a project with AGENTS.md and AGENTS-DEV.md
        eclipseWriteFile("AGENTS.md", "(Global Rules)");
        eclipseWriteFile("AGENTS-DEV.md", "Dev agent content");

        // AND Peon-Dev is the active agent
        agentsMdService.load(project);
        agentsMdService.setAgentNameSupplier(() -> "Peon-Dev");

        // WHEN
        var items = agentsMdService.get();

        // THEN two items — base + agent file, each with its own full path as label
        assertEquals(2, items.size());
        var basePath = "/" + project.getName() + "/AGENTS.md";
        var agentPath = "/" + project.getName() + "/AGENTS-DEV.md";
        assertEquals(basePath, items.get(0).label());
        assertEquals(agentPath, items.get(1).label());

        // AND each dedupKey is the exact ADR-0029 header of its own label
        assertEquals(basePath + ":" + System.lineSeparator() + "---" + System.lineSeparator(), items.get(0).dedupKey());
        assertEquals(agentPath + ":" + System.lineSeparator() + "---" + System.lineSeparator(), items.get(1).dedupKey());
    }

    @Test
    public void test_agentsMd_itemsFor_agentFileMissing_onlyBase() {
        // GIVEN a project with only AGENTS.md (no AGENTS-DEV.md)
        eclipseWriteFile("AGENTS.md", "(Global Rules)");
        eclipseDeleteResource("AGENTS-DEV.md");

        // AND Peon-Dev is the active agent
        agentsMdService.load(project);
        agentsMdService.setAgentNameSupplier(() -> "Peon-Dev");

        // WHEN
        var items = agentsMdService.get();

        // THEN only the base item
        assertEquals(1, items.size());
        assertEquals("/" + project.getName() + "/AGENTS.md", items.get(0).label());
    }

    @Test
    public void test_agentsMd_itemsFor_noBase_empty() {
        // GIVEN a project without AGENTS.md (and without the agent file)
        eclipseDeleteResource("AGENTS.md");
        eclipseDeleteResource("AGENTS-DEV.md");

        // AND Peon-Dev is the active agent
        agentsMdService.load(project);
        agentsMdService.setAgentNameSupplier(() -> "Peon-Dev");

        // WHEN
        var items = agentsMdService.get();

        // THEN no items at all
        assertTrue(items.isEmpty());
    }
}
