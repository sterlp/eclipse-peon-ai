package org.sterl.llmpeon.test;

import static org.junit.Assert.assertNotNull;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.internal.core.util.SimpleDocument;
import org.eclipse.jface.text.TextSelection;
import org.junit.Test;
import org.sterl.llmpeon.StandingOrdersBuilder;
import org.sterl.llmpeon.parts.PeonAiService;
import org.sterl.llmpeon.parts.model.UserContext;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.JdtUtil;

public class StandingOrdersBuilderTest extends AbstractTest {
    @Test
    public void test_AgentsMdService() {
        // GIVEN
        PeonAiService aiService = new PeonAiService(null, null, null);
        aiService.setProject(project);
        StandingOrdersBuilder standingOrders = new StandingOrdersBuilder()
                .add(aiService)
                .add(aiService.getAgentsMdService());
        
        // WHEN
        var messages = standingOrders.build();
        
        // THAN agents md
        assertHasMessageWith(messages, "/AGENTS.md");
        assertHasMessageWith(messages, "(Global Rules)");
        // AND no nulls ... 
        assertHasNoMessageWith(messages, " null");
    }
    
    @Test
    public void test_user_context() {
        // GIVEN
        UserContext userContext = new UserContext();
        StandingOrdersBuilder standingOrders = new StandingOrdersBuilder()
                .add(userContext);
        
        // WHEN
        userContext.setCurrentProject(project);
        var messages = standingOrders.build();
        
        // THEN
        assertHasMessageWith(messages, project.getName());
        assertHasMessageWith(messages, JdtUtil.diskPathOf(project));

        // AND no nulls ... 
        assertHasNoMessageWith(messages, " null");
    }
    
    @Test
    public void test_one_time_order_flows_through_and_is_consumed() {
        // GIVEN — a command/skill body added as a one-time order
        var standingOrders = new StandingOrdersBuilder();
        standingOrders.addOneTimeOrder("Review the code and report any issues.");

        // WHEN
        var messages = standingOrders.build();

        // THEN — the one-time order is part of the built standing orders
        assertHasMessageWith(messages, "Review the code and report any issues.");

        // AND — it is consumed: a second build no longer contains it
        var second = standingOrders.build();
        assertHasNoMessageWith(second, "Review the code and report any issues.");
    }

    @Test
    public void test_one_time_order_appended_after_providers() {
        // GIVEN — providers plus a command one-time order
        PeonAiService aiService = new PeonAiService(null, null, null);
        aiService.setProject(project);
        var standingOrders = new StandingOrdersBuilder()
                .add(aiService)
                .add(aiService.getAgentsMdService());
        standingOrders.addOneTimeOrder("Review the code and report any issues.");

        // WHEN
        var messages = standingOrders.build();

        // THEN — provider content and the command body both present
        assertHasMessageWith(messages, "/AGENTS.md");
        assertHasMessageWith(messages, "Review the code and report any issues.");

        // AND — the one-time order is consumed
        var second = standingOrders.build();
        assertHasNoMessageWith(second, "Review the code and report any issues.");
    }

    @Test
    public void test_file_selection_with_text_range() {
        // GIVEN - selected file is pom.xml with text selection lines 1-2
        var userContext = new UserContext();
        var standingOrders = new StandingOrdersBuilder()
                .add(userContext);

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
        var messages = standingOrders.build();
        
        // THEN - should contain path to pom.xml
        assertHasMessageWith(messages, "pom.xml");
        
        // AND start marker <project should be present (line 1)
        assertHasMessageWith(messages, "<project");
        
        // AND end marker </project> should be present
        assertHasMessageWith(messages, "</project>");
        
        // AND
        assertHasNoMessageWith(messages, "das sollten wir nicht sehen");
    }
}
