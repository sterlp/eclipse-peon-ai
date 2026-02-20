package org.sterl.llmpeon.skill;

import java.nio.file.Files;
import java.nio.file.Path;

public record SkillRecord(String name, String description, Path skillFile) {

    public String readFullContent() {
        try {
            return Files.readString(skillFile);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read " + skillFile, e);
        }
    }
    
    public String shortDescription() {
        return "Skill[name="+ name + ",description=" + description + "]";
    }
}
