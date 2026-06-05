package org.sterl.llmpeon.skill;

import java.nio.file.Path;

import org.sterl.llmpeon.shared.AbstractPromptFile;

public class SkillPromptFile extends AbstractPromptFile {

    public SkillPromptFile(String name, String description, Path promptFile) {
        super(name, description, promptFile, true, null);
    }

    @Override
    public String readBody() {
        return "Path: " + getPromptFile().getParent() + "\n---\n" + super.readBody();
    }
}