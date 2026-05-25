package org.sterl.llmpeon;

import java.util.function.Predicate;

import org.sterl.llmpeon.ai.ConfiguredModel;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.template.TemplateContext;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;
import org.sterl.llmpeon.tool.tools.CompactSessionTool;

public class AiDeveloperService extends AbstractChatService {

    private static final String BASE_PROMPT = PromptLoader.loadWithDefault("developer.txt");

    public AiDeveloperService(ConfiguredModel configuredModel,
            ToolService toolService,
            SkillService skillService, TemplateContext templateContext) {
        super(configuredModel, toolService, skillService, templateContext);
    }

    @Override
    protected String getSystemPrompt() {
        return templateContext.process(BASE_PROMPT);
    }

    protected double getTemperature() {
        return configuredModel.getConfig().getDevTemperature();
    }

    @Override
    protected Predicate<SmartToolExecutor> getToolFilter() {
        return t -> {
            if (t.getTool() instanceof CompactSessionTool) {
                return getTokenSize() > configuredModel.getConfig().getTokenWindow() * 0.4
                        && getMessages().size() > 5;
            }
            return true;
        };
    }
}
