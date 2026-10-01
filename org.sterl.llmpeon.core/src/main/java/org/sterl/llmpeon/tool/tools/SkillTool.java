package org.sterl.llmpeon.tool.tools;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.skill.SkillPromptFile;
import org.sterl.llmpeon.skill.SkillService;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class SkillTool extends AbstractTool {

    private final SkillService skillService;

    /** R1 skill-evolution-loop: max ~2 lines, token discipline — ask is part of the read result. */
    private static final String USEFULNESS_FOOTER =
            "Report in your answer — helpful? wrong/outdated/incomplete? obsolete?";

    public SkillTool(SkillService skillService) {
        super();
        this.skillService = skillService;
    }
    
    @Tool("Read a skill's prompt by name. See skillList for available skills.")
    public String skillRead(@P(name = "name") String name) throws IOException, InterruptedException {
        ArgsUtil.requireNonBlank(name, "name");
        var skill = skillService.get(name);
        if (skill.isEmpty()) {
            onProblem("SKILL " + name + " not found ...");
            return "No skill with the name " + name 
                    + " found. Use one of: " + skillService.skillNames();
        }
        String result = withUsefulnessFooter(skill.get().renderBody());
        onTool("Reading SKILL 🧩 " + name + " " + StringUtil.charsSuffix(result));
        return result;
    }
    
    @Tool("List all available skills with short descriptions. Call before complex tasks to discover relevant skills.")
    public String skillList() throws IOException, InterruptedException {
        List<SkillPromptFile> skills = skillService.getSkills();
        String result = skills.isEmpty() 
                ? "No skills available"
                : skills.stream().map(SkillPromptFile::buildShortInfo).collect(Collectors.joining("\n"));
        onTool("List SKILLs 🧩: " + skills.size() + " " + StringUtil.charsSuffix(result));
        return result;
    }

    @Tool("Read a file from a skill's directory by relative path. For templates and configs.")
    public String skillReadFile(@P(name = "name", description = "skill name") String name, @P(name = "path") String path) throws IOException {
        ArgsUtil.requireNonBlank(name, "name");
        ArgsUtil.requireNonBlank(path, "path");
        var skill = skillService.get(name);
        if (skill.isEmpty()) {
            onProblem("SKILL " + name + " not found ...");
            return "No skill with the name " + name
                    + " found. Use one of: " + skillService.skillNames();
        }
        String result = withUsefulnessFooter(skill.get().readRelativeFile(path));
        onTool("Read file from SKILL " + name + ": " + path + " " + StringUtil.charsSuffix(result));
        return result;
    }

    private static String withUsefulnessFooter(String content) {
        return content + System.lineSeparator() + USEFULNESS_FOOTER;
    }
}