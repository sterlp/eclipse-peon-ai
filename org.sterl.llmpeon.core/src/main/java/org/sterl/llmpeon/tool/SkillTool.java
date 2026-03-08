package org.sterl.llmpeon.tool;

import java.io.IOException;

import org.sterl.llmpeon.skill.SkillService;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class SkillTool extends AbstractTool {

    private final SkillService skillService;

    public SkillTool(SkillService skillService) {
        super();
        this.skillService = skillService;
    }
    
    @Tool("Read the SKILL.md directly using the name of the skill.")
    public String readSkill(@P("SKILL name") String name) throws IOException, InterruptedException {
        var skill = skillService.get(name);
        if (skill.isEmpty()) return "No skill with the name " + name + " found. Use one of: " 
                + skillService.skillNames();
        monitorMessage("Read SKILL " + name);
        return skill.get().readFullContent();
    }
}
