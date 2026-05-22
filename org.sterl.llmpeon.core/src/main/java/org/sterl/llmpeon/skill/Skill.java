package org.sterl.llmpeon.skill;

import java.nio.file.Files;
import java.nio.file.Path;

public class Skill {
    private final String name;
    private final String description;
    private final Path skillFile;
    private boolean enabled = true;

    public Skill(String name, String description, Path skillFile) {
        this.name = name;
        this.description = description;
        this.skillFile = skillFile;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Path skillFile() {
        return skillFile;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String readFullContent() {
        try {
            return Files.readString(skillFile);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read " + skillFile, e);
        }
    }

    public String shortDescription() {
        return "Skill[name="+ name + ", description=" + description + "]";
    }
}
