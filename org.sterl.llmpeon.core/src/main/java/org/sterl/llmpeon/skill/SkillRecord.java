package org.sterl.llmpeon.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record SkillRecord(String name, String description, Path skillFile) {

    public String readFullContent() throws IOException {
        return Files.readString(skillFile);
    }
}
