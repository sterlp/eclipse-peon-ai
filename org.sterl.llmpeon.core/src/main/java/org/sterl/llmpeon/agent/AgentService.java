package org.sterl.llmpeon.agent;

import org.sterl.llmpeon.template.TemplateContext;

import dev.langchain4j.model.chat.ChatModel;

public class AgentService {

    private ChatModel model;

    public AgentService(ChatModel model) {
        this.model = model;
    }

    public void updateModel(ChatModel model) {
        this.model = model;
    }

    public ChatModel getModel() {
        return model;
    }

    public AiDeveloperAgent newDeveloperAgent(TemplateContext context) {
        return new AiDeveloperAgent(model, context);
    }

    public AiDeveloperAgent newDeveloperAgent(TemplateContext context, boolean agentMode) {
        return new AiDeveloperAgent(model, context, agentMode);
    }

    public AiPlannerAgent newPlannerAgent(TemplateContext context) {
        return new AiPlannerAgent(model, context);
    }

    public AiPlannerAgent newPlannerAgent(TemplateContext context, boolean agentMode) {
        return new AiPlannerAgent(model, context, agentMode);
    }

    public AiSearchAgent newSearchAgent() {
        return new AiSearchAgent(model);
    }

    public AiCompressorAgent newCompressorAgent() {
        return new AiCompressorAgent(model);
    }
}
