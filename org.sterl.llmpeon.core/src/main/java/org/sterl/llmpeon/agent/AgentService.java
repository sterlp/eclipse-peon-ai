package org.sterl.llmpeon.agent;

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

    public AiDeveloperAgent newDeveloperAgent() {
        return new AiDeveloperAgent(model);
    }

    public AiPlannerAgent newPlannerAgent() {
        return new AiPlannerAgent(model);
    }

    public AiSearchAgent newSearchAgent() {
        return new AiSearchAgent(model);
    }

    public AiCompressorAgent newCompressorAgent() {
        return new AiCompressorAgent(model);
    }
}
