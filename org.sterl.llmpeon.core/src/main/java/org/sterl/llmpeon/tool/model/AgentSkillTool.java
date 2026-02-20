package org.sterl.llmpeon.tool.model;

import java.nio.file.Files;
import java.util.stream.Collectors;

import org.sterl.llmpeon.shared.FileUtils;
import org.sterl.llmpeon.skill.SkillRecord;
import org.sterl.llmpeon.skill.SkillService;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

@Deprecated // AI gets confused together with the readFileTool -.-
public class AgentSkillTool extends AbstractTool {

    private SkillService service;

    public AgentSkillTool(SkillService service) {
        super();
        this.service = service;
    }
    
    public void setService(SkillService service) {
        this.service = service;
    }

    @Tool("Reads skills using their name directly if known - SKILL.md, which may contain subsctructures and files.")
    public String readAgentSkillByName(
            @P("Name of the skill which should be loaded") String name) {
        var skill = service.get(name);
        if (skill.isPresent()) {
            return skill.get().readFullContent();
        } else {
            monitorMessage("Skill " + name + " not found!");
            return "Skill " + name + " not found use one of " + service.getSkills().stream()
                    .map(SkillRecord::name)
                    .collect(Collectors.joining(", "));
        }
    }
    
    @Tool("Reads the sub content / file of a sub page to an skill by its name.")
    public String readSkillSubContent(
            @P("Name of the skill to which the content should be loaded") String name,
            @P("Relative path to the sub content or page metioned in the skill itself") String path) {
        var skill = service.get(name);
        
        if (skill.isPresent()) {
            var skillParent = skill.get().skillFile().getParent();
            var filePath = skillParent.resolve(path);
            
            if (Files.isRegularFile(filePath)) return FileUtils.readString(filePath);
            var file = FileUtils.findFirst(skillParent, name);
            // found but wrong path
            if (file.isPresent()) {
                return "Found but the path seems to be wrong, as the developer if a fix should be applied"
                        + "\nSkill: " + skill.get().skillFile()
                        + "\nSkill path: " + skillParent
                        + "\nGiven path: " + path
                        + "\nFound in: " + file
                        + "\n" + FileUtils.readString(file.get());
            } else {
                return "Could not find path ask the developer"
                        + "\nSkill: " + skill.get().skillFile()
                        + "\nSkill path: " + skillParent
                        + "\nGiven path: " + path;
            }
            
        } else {
            monitorMessage("Skill " + name + " not found!");
            return "Skill " + name + " not found use one of " + service.getSkills().stream()
                    .map(SkillRecord::name)
                    .collect(Collectors.joining(", "));
        }
    }
    
    @Override
    public boolean isActive() {
        return service != null && !service.getSkills().isEmpty();
    }
}
