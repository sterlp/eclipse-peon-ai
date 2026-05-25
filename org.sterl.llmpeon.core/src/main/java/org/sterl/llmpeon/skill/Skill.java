package org.sterl.llmpeon.skill;

import java.nio.file.Path;

import org.sterl.llmpeon.shared.AbstractPromptFile;

public class Skill extends AbstractPromptFile {

    public Skill(String name, String description, Path promptFile) {
        super(name, description, promptFile, true);
    }

    @Override
    public String readBody() {
        return "Path: " + getPromptFile().getParent() + "\n---\n" + super.readBody();
    }

    @Override
    public String shortDescription() {
        return "Skill[name=" + name() + ", description=" + description() + "]";
    }
}