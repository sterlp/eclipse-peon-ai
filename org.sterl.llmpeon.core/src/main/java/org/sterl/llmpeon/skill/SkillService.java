package org.sterl.llmpeon.skill;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SkillService {

    private final Path skillsDirectory;
    private volatile List<SkillRecord> skills = Collections.emptyList();

    public SkillService(Path skillsDirectory) {
        this.skillsDirectory = skillsDirectory;
    }

    public List<SkillRecord> getSkills() {
        return skills;
    }

    public void refresh() throws IOException {
        List<SkillRecord> result = new ArrayList<>();

        if (Files.isDirectory(skillsDirectory)) {
            try (DirectoryStream<Path> dirs = Files.newDirectoryStream(skillsDirectory)) {
                for (Path dir : dirs) {
                    Path skillFile = dir.resolve("SKILL.md");
                    if (!Files.isRegularFile(skillFile)) skillFile = dir.resolve("skill.md");
                    if (Files.isDirectory(dir) && Files.isRegularFile(skillFile)) {
                        SkillRecord skill = parseSkillFile(skillFile);
                        if (skill != null) {
                            result.add(skill);
                        }
                    }
                }
            }
        }

        this.skills = Collections.unmodifiableList(result);
    }

    static SkillRecord parseSkillFile(Path skillFile) throws IOException {
        String name = null;
        String description = null;
        boolean inFrontmatter = false;

        try (BufferedReader reader = Files.newBufferedReader(skillFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                if (!inFrontmatter) {
                    if ("---".equals(trimmed)) {
                        inFrontmatter = true;
                    }
                    continue;
                }

                // end of frontmatter
                if ("---".equals(trimmed)) {
                    break;
                }

                if (trimmed.startsWith("name:")) {
                    name = stripYamlValue(trimmed.substring(5));
                } else if (trimmed.startsWith("description:")) {
                    description = stripYamlValue(trimmed.substring(12));
                }
            }
        }

        if (name != null && description != null) {
            return new SkillRecord(name, description, skillFile);
        }
        return null;
    }

    private static String stripYamlValue(String value) {
        if (value == null) return null;
        value = value.strip();
        // remove surrounding quotes
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                 || (value.startsWith("'") && value.endsWith("'")))) {
            value = value.substring(1, value.length() - 1);
        }
        return value.isEmpty() ? null : value;
    }
}
