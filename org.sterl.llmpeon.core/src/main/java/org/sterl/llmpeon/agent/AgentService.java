package org.sterl.llmpeon.agent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.sterl.llmpeon.tool.ToolService;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.ChatModel;

public class AgentService {

    private ChatModel model;
    private final Set<String> agentToolNames = new HashSet<>();

    public AgentService(ChatModel model) {
        this.model = model;
    }

    public void updateModel(ChatModel model) {
        this.model = model;
    }

    public ChatModel getModel() {
        return model;
    }

    /**
     * Register a tool name as "agent-level". Agent tools are excluded from the
     * tool list passed to sub-agents (prevents recursive sub-agent calls).
     */
    public void registerAgentTool(String toolName) {
        agentToolNames.add(toolName);
    }

    /**
     * Returns all tool specs from the given ToolService, minus any registered
     * agent tools. Safe to pass to sub-agents.
     */
    public List<ToolSpecification> nonAgentToolSpecs(ToolService toolService) {
        return toolService.toolSpecifications().stream()
                .filter(s -> !agentToolNames.contains(s.name()))
                .toList();
    }

    public AiDeveloperAgent newDeveloperAgent() {
        return new AiDeveloperAgent(model);
    }

    public AiSearchAgent newSearchAgent() {
        return new AiSearchAgent(model);
    }

    public AiCompressorAgent newCompressorAgent() {
        return new AiCompressorAgent(model);
    }
}
