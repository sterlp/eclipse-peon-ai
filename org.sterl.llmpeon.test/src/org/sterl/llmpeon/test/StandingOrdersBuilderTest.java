package org.sterl.llmpeon.test;

import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.stream.Collectors;

import org.junit.Test;
import org.sterl.llmpeon.parts.PeonAiService;
import org.sterl.llmpeon.parts.StandingOrdersBuilder;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.JdtUtil;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;

public class StandingOrdersBuilderTest extends AbstractTest {
    
    @Test
    public void test_emptyBuild_returnsEmptyList() {
        // GIVEN
        PeonAiService aiService = new PeonAiService(null, null, null);
        aiService.setProject(project);
        StandingOrdersBuilder standingOrders = new StandingOrdersBuilder(aiService.getTemplateContext())
                .add(aiService)
                .add(aiService.getAgentsMdService());
        
        // WHEN
        var messages = standingOrders.build(project, EclipseUtil.getOpenFile().orElse(null));
        
        // THEN
        assertHasUserMessageWith(messages, project.getName());
        assertHasUserMessageWith(messages, JdtUtil.diskPathOf(project));
        
        // AND agents md
        assertHasUserMessageWith(messages, "/org.sterl.llmpeon.test/AGENTS.md");
        // AND no nulls ... 
        assertHasNoUserMessageWith(messages, " null");
    }
    
    public static void assertHasUserMessageWith(Collection<ChatMessage> messages, String content) {
        var textMessages = messages.stream()
            .filter(m -> m instanceof UserMessage)
            .map(m -> ((UserMessage)m).singleText())
            .toList();
        
        var match = textMessages.stream().filter(m -> m.contains(content)).findAny();
        assertTrue("Could not find: \n" + content
                + "\nin:\n" + textMessages.stream().collect(Collectors.joining("\n")), 
                match.isPresent());
    }
    
    public static void assertHasNoUserMessageWith(Collection<ChatMessage> messages, String content) {
        var textMessages = messages.stream()
            .filter(m -> m instanceof UserMessage)
            .map(m -> ((UserMessage)m).singleText())
            .toList();
        
        var match = textMessages.stream().filter(m -> m.contains(content)).findAny();
        assertTrue("Found match: \n" + content
                + "\nin:\n" + match.orElse(null), 
                match.isEmpty());
    }
}
