package org.sterl.llmpeon.shared;

import java.nio.file.Files;
import java.nio.file.Path;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
public abstract class AbstractPromptFile {

    private final String name;
    private final String description;
    private final Path promptFile;
    private volatile boolean enabled = true;
    
    StringBuilder info;
    public String buildShortInfo() {
        if (info == null) {
            info = new StringBuilder();
            info.append("---\nname: ").append(name)
                .append("\ndescription: " + description);
        }
        return info.toString();
    }

    public String name() {
        return getName();
    }

    public String description() {
        return getDescription();
    }

    public Path promptFile() {
        return getPromptFile();
    }

    public String readFullContent() {
        try {
            return Files.readString(promptFile);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read " + promptFile, e);
        }
    }
    
    public boolean isEnabled() {
        return enabled && Files.exists(promptFile);
    }

    public String readBody() {
        return PromptYmlParser.stripFrontmatter(readFullContent());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[name=" + name + ", promptFile: " + promptFile + "]";
    }
}