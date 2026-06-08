package org.sterl.llmpeon.tool.tools;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.skill.SkillPromptFile;
import org.sterl.llmpeon.skill.SkillService;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class SkillTool extends AbstractTool {

    private final SkillService skillService;

    public SkillTool(SkillService skillService) {
        super();
        this.skillService = skillService;
    }
    
    @Tool("Load/read an SKILL using its name.")
    public String readSkill(@P(name = "name") String name) throws IOException, InterruptedException {
        ArgsUtil.requireNonBlank(name, "name");
        var skill = skillService.get(name);
        if (skill.isEmpty()) {
            onProblem("SKILL " + name + " not found ...");
            return "No skill with the name " + name 
                    + " found. Use one of: " + skillService.skillNames();
        }
        onTool("Read SKILL " + name);
        return skill.get().readBody();
    }
    
    @Tool("List all active SKILL - use it before complex tasks, to verify if a skill is available.")
    public String listSkill() throws IOException, InterruptedException {
        onTool("List SKILLs");
        List<SkillPromptFile> skills = skillService.getSkills();
        return skills.isEmpty() 
                ? "No skills available"
                : skills.stream().map(SkillPromptFile::buildShortInfo).collect(Collectors.joining("\n"));
    }

    @Tool("Read a file from a SKILL directory using its relative path.")
    public String readSkillFile(@P(name = "name", description = "skill name") String name, @P(name = "path") String path) throws IOException {
        ArgsUtil.requireNonBlank(name, "name");
        ArgsUtil.requireNonBlank(path, "path");
        var skill = skillService.get(name);
        if (skill.isEmpty()) {
            onProblem("SKILL " + name + " not found ...");
            return "No skill with the name " + name
                    + " found. Use one of: " + skillService.skillNames();
        }
        onTool("Read file from SKILL " + name + ": " + path);
        return skill.get().readRelativeFile(path);
    }
}