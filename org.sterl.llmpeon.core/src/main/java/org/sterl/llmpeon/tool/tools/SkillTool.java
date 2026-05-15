package org.sterl.llmpeon.tool.tools;

import java.io.IOException;

import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.skill.SkillService;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class SkillTool extends AbstractTool {

    private final SkillService skillService;

    public SkillTool(SkillService skillService) {
        super();
        this.skillService = skillService;
    }
    
    @Tool("Read an available SKILL using its name.")
    public String readSkill(@P(name = "name") String name) throws IOException, InterruptedException {
        ArgsUtil.requireNonBlank(name, "name");
        var skill = skillService.get(name);
        if (skill.isEmpty()) {
            onProblem("No SKILL " + name + " found ...");
            return "No skill with the name " + name 
                    + " found. Use one of: " + skillService.skillNames();
        }
        onTool("Read SKILL " + name);
        return skill.get().readFullContent();
    }
}
