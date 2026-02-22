package org.sterl.llmpeon.tool;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.skill.SkillRecord;
import org.sterl.llmpeon.skill.SkillService;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;

/**
 * Owns all tools and the tool registry.
 * Tools are stable instances — only the context changes when the user selects a different file/project.
 */
public class ToolService {

    private final Map<String, SmartToolExecutor> toolExecutors = new HashMap<>();
    private SkillService skillService;

    public ToolService() {
        super();
    }

    public List<ToolSpecification> toolSpecifications() {
        return toolExecutors.values().stream()
                .filter(SmartToolExecutor::isActive)
                .map(SmartToolExecutor::getSpec)
                .toList();
    }

    public SmartToolExecutor getExecutor(String toolName) {
        return toolExecutors.get(toolName);
    }
    
    public ChatMessage skillMessage() {
        if (skillService  == null || skillService.getSkills().isEmpty()) return null;
        var string = skillService.getSkills().stream().map(s -> s.toString()).collect(Collectors.joining("\n"));
        return SystemMessage.from("Following skills are availble use load and use them as soon the nee arises\n" + string);
    }

    /**
     * Registers any object that has methods annotated with {@link Tool}.
     * Existing tools with the same name will be replaced.
     */
    public void addTool(SmartTool toolObject) {
        for (Method method : toolObject.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                var spec = ToolSpecifications.toolSpecificationFrom(method);
                var old = toolExecutors.put(spec.name(), new SmartToolExecutor(toolObject, method, spec));
                if (old != null) throw new RuntimeException("Tool with " + spec.name() + " already registered ...");
                System.out.println("added tool " + spec);
            }
        }
    }

    /**
     * Updates the skill directory and reloads all skills.
     * Called when config changes or a new project root is set.
     */
    public void updateSkillDirectory(String skillDirectory) {
        if (StringUtil.hasNoValue(skillDirectory)) {
            this.skillService = null;
        } else {
            this.skillService = new SkillService(Path.of(skillDirectory));
            try {
                this.skillService.refresh();
            } catch (IOException e) {
                throw new RuntimeException("Could not load skills from " + skillDirectory);
            }
        }
    }

    public List<SkillRecord> getSkills() {
        if (skillService == null) return Collections.emptyList();
        return skillService.getSkills();
    }
}
