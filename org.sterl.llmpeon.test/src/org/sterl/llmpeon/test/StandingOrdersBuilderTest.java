package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Path;
import org.junit.Test;
import org.sterl.llmpeon.PeonMode;
import org.sterl.llmpeon.parts.StandingOrdersBuilder;
import org.sterl.llmpeon.parts.agent.AgentModeService;
import org.sterl.llmpeon.parts.agentsmd.AgentsMdService;
import org.sterl.llmpeon.template.TemplateContext;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;

public class StandingOrdersBuilderTest {

    private final AgentsMdService agentsMdService = new MockAgentsMdService();
    private final AgentModeService agentModeService = new MockAgentModeService();
    private final TemplateContext context = new TemplateContext("/tmp");

    // -------------------------------------------------------------------------
    // Selected resource tests
    // -------------------------------------------------------------------------

    @Test
    public void test_emptyBuild_returnsEmptyList() {
        List<ChatMessage> orders = StandingOrdersBuilder.build(
                null, agentsMdService, context, PeonMode.DEV, agentModeService, null);
        assertEquals(0, orders.size());
    }

    @Test
    public void test_selectedResource_addsFilePathAndDiskPath() {
        IResource resource = mock(IResource.class);
        IProject project = mock(IProject.class);
        when(resource.getFullPath()).thenReturn(new Path("/MyProj/src/Foo.java"));
        when(resource.getProject()).thenReturn(project);
        when(project.getRawLocation()).thenReturn(new Path("/home/user/workspace/MyProj"));

        List<ChatMessage> orders = StandingOrdersBuilder.build(
                resource, agentsMdService, context, PeonMode.DEV, agentModeService, null);

        assertEquals(1, orders.size());
        assertTrue(orders.get(0) instanceof SystemMessage);
        String text = ((SystemMessage) orders.get(0)).text();
        assertTrue(text.contains("Eclipse selected file filePath: /MyProj/src/Foo.java"));
        assertTrue(text.contains("Disk path of eclipse project: /home/user/workspace/MyProj"));
    }

    @Test
    public void test_selectedResource_nullDiskPath_omitsDiskPath() {
        IResource resource = mock(IResource.class);
        IProject project = mock(IProject.class);
        when(resource.getFullPath()).thenReturn(new Path("/MyProj/src/Foo.java"));
        when(resource.getProject()).thenReturn(project);
        when(project.getRawLocation()).thenReturn(null);
        when(project.getLocation()).thenReturn(null);

        List<ChatMessage> orders = StandingOrdersBuilder.build(
                resource, agentsMdService, context, PeonMode.DEV, agentModeService, null);

        assertEquals(1, orders.size());
        String text = ((SystemMessage) orders.get(0)).text();
        assertTrue(text.contains("Eclipse selected file filePath: /MyProj/src/Foo.java"));
        assertFalse(text.contains("Disk path of eclipse project:"));
    }

    // -------------------------------------------------------------------------
    // AGENTS.md tests
    // -------------------------------------------------------------------------

    @Test
    public void test_agentsMdEnabled_andFileFound_addsAiMessage() {
        ((MockAgentsMdService) agentsMdService).setAgentMessage(AiMessage.from("# Project Rules\nAlways use Lombok."));
        List<ChatMessage> orders = StandingOrdersBuilder.build(
                null, agentsMdService, context, PeonMode.DEV, agentModeService, null);

        assertEquals(1, orders.size());
        assertTrue(orders.get(0) instanceof AiMessage);
        assertTrue(((AiMessage) orders.get(0)).text().contains("# Project Rules"));
    }

    @Test
    public void test_agentsMdDisabled_returnsEmpty() {
        ((MockAgentsMdService) agentsMdService).setAgentMessage(null);
        List<ChatMessage> orders = StandingOrdersBuilder.build(
                null, agentsMdService, context, PeonMode.DEV, agentModeService, null);
        assertEquals(0, orders.size());
    }

    // -------------------------------------------------------------------------
    // Memory message tests
    // -------------------------------------------------------------------------

    @Test
    public void test_memoryMessage_addedToOrders() {
        ChatMessage memoryMsg = SystemMessage.from("Previous conversation summary...");
        List<ChatMessage> orders = StandingOrdersBuilder.build(
                null, agentsMdService, context, PeonMode.DEV, agentModeService, memoryMsg);

        assertEquals(1, orders.size());
        assertEquals(memoryMsg, orders.get(0));
    }

    // -------------------------------------------------------------------------
    // Agent mode plan tests
    // -------------------------------------------------------------------------

    @Test
    public void test_agentModeWithPlan_addsPlanHint() {
        ((MockAgentModeService) agentModeService).setHasPlan(true);
        ((MockAgentModeService) agentModeService).setPlanPathHint("Plan file: /MyProj/.plan/overview.md");

        List<ChatMessage> orders = StandingOrdersBuilder.build(
                null, agentsMdService, context, PeonMode.AGENT, agentModeService, null);

        assertEquals(1, orders.size());
        assertTrue(orders.get(0) instanceof SystemMessage);
        assertTrue(((SystemMessage) orders.get(0)).text().contains("Plan file:"));
    }

    @Test
    public void test_agentModeWithoutPlan_noPlanHint() {
        ((MockAgentModeService) agentModeService).setHasPlan(false);
        List<ChatMessage> orders = StandingOrdersBuilder.build(
                null, agentsMdService, context, PeonMode.AGENT, agentModeService, null);
        assertEquals(0, orders.size());
    }

    @Test
    public void test_nonAgentMode_withPlan_noPlanHint() {
        ((MockAgentModeService) agentModeService).setHasPlan(true);
        List<ChatMessage> orders = StandingOrdersBuilder.build(
                null, agentsMdService, context, PeonMode.DEV, agentModeService, null);
        assertEquals(0, orders.size());
    }

    @Test
    public void test_planMode_noPlanHint() {
        ((MockAgentModeService) agentModeService).setHasPlan(true);
        List<ChatMessage> orders = StandingOrdersBuilder.build(
                null, agentsMdService, context, PeonMode.PLAN, agentModeService, null);
        assertEquals(0, orders.size());
    }

    // -------------------------------------------------------------------------
    // Combined tests
    // -------------------------------------------------------------------------

    @Test
    public void test_fullBuild_allPartsPresent() {
        IResource resource = mock(IResource.class);
        IProject project = mock(IProject.class);
        when(resource.getFullPath()).thenReturn(new Path("/MyProj/src/Foo.java"));
        when(resource.getProject()).thenReturn(project);
        when(project.getRawLocation()).thenReturn(new Path("/home/user/workspace/MyProj"));

        ((MockAgentsMdService) agentsMdService).setAgentMessage(AiMessage.from("# Rules"));
        ChatMessage memoryMsg = SystemMessage.from("Memory...");
        ((MockAgentModeService) agentModeService).setHasPlan(true);
        ((MockAgentModeService) agentModeService).setPlanPathHint("Plan file: /MyProj/.plan/overview.md");

        List<ChatMessage> orders = StandingOrdersBuilder.build(
                resource, agentsMdService, context, PeonMode.AGENT, agentModeService, memoryMsg);

        assertEquals(4, orders.size());
        assertTrue(((SystemMessage) orders.get(0)).text().contains("Eclipse selected file filePath:"));
        assertTrue(orders.get(1) instanceof AiMessage);
        assertTrue(((AiMessage) orders.get(1)).text().contains("# Rules"));
        assertEquals("Memory...", ((SystemMessage) orders.get(2)).text());
        assertTrue(((SystemMessage) orders.get(3)).text().contains("Plan file:"));
    }

    @Test
    public void test_orderIsCorrect_filePath_thenAgentsMd_thenMemory_thenPlan() {
        IResource resource = mock(IResource.class);
        IProject project = mock(IProject.class);
        when(resource.getFullPath()).thenReturn(new Path("/P/F.java"));
        when(resource.getProject()).thenReturn(project);
        when(project.getRawLocation()).thenReturn(null);

        ((MockAgentsMdService) agentsMdService).setAgentMessage(AiMessage.from("agents content"));
        ChatMessage memoryMsg = SystemMessage.from("mem");
        ((MockAgentModeService) agentModeService).setHasPlan(true);
        ((MockAgentModeService) agentModeService).setPlanPathHint("plan hint");

        List<ChatMessage> orders = StandingOrdersBuilder.build(
                resource, agentsMdService, context, PeonMode.AGENT, agentModeService, memoryMsg);

        assertEquals(4, orders.size());
        assertTrue(((SystemMessage) orders.get(0)).text().contains("Eclipse selected file filePath:"));
        assertTrue(orders.get(1) instanceof AiMessage);
        assertEquals(memoryMsg, orders.get(2));
        assertTrue(((SystemMessage) orders.get(3)).text().contains("plan hint"));
    }

    @Test
    public void test_agentsMdOnly_noResourceNoMemoryNoPlan() {
        ((MockAgentsMdService) agentsMdService).setAgentMessage(AiMessage.from("rule: be nice"));
        List<ChatMessage> orders = StandingOrdersBuilder.build(
                null, agentsMdService, context, PeonMode.DEV, agentModeService, null);

        assertEquals(1, orders.size());
        assertTrue(orders.get(0) instanceof AiMessage);
    }

    @Test
    public void test_resourceAndMemoryOnly() {
        IResource resource = mock(IResource.class);
        IProject project = mock(IProject.class);
        when(resource.getFullPath()).thenReturn(new Path("/P/F.java"));
        when(resource.getProject()).thenReturn(project);
        when(project.getRawLocation()).thenReturn(new Path("/disk/P"));

        ChatMessage memoryMsg = SystemMessage.from("recall: use lombok");

        List<ChatMessage> orders = StandingOrdersBuilder.build(
                resource, agentsMdService, context, PeonMode.DEV, agentModeService, memoryMsg);

        assertEquals(2, orders.size());
        assertTrue(((SystemMessage) orders.get(0)).text().contains("Eclipse selected file filePath:"));
        assertEquals(memoryMsg, orders.get(1));
    }

    // -------------------------------------------------------------------------
    // Lightweight mocks for services (no Mockito needed — plain Java classes)
    // -------------------------------------------------------------------------

    private static class MockAgentsMdService extends AgentsMdService {
        private AiMessage agentMsg = null;
        void setAgentMessage(AiMessage msg) { this.agentMsg = msg; }
        @Override public Optional<AiMessage> agentMessage(TemplateContext context) {
            return agentMsg != null ? Optional.of(agentMsg) : Optional.empty();
        }
    }

    private static class MockAgentModeService extends AgentModeService {
        private boolean hasPlan = false;
        private String planPathHint = "";
        void setHasPlan(boolean value) { this.hasPlan = value; }
        void setPlanPathHint(String hint) { this.planPathHint = hint; }
        @Override public boolean hasPlan() { return hasPlan; }
        @Override public String planPathHint() { return planPathHint; }

        MockAgentModeService() {
            super(null, null, () -> {});
        }
    }
}
