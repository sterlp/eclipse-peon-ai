package org.sterl.open.aifactory.project.dto;

import java.util.List;

/**
 * One directory of a read root. {@code path} is the request's path relative to that root — never a
 * real path, which is the whole point of the area being read-only (R-WP-14 / UC-WP-42).
 */
public record ProjectDirectoryDto(String kind, String root, String path, List<ProjectEntryDto> entries,
        boolean truncated) {

    public static final String KIND = "directory";

    public ProjectDirectoryDto {
        entries = List.copyOf(entries);
    }

    public static ProjectDirectoryDto of(ProjectFileRoot root, String path, List<ProjectEntryDto> entries,
            boolean truncated) {
        return new ProjectDirectoryDto(KIND, root.wireName(), path, entries, truncated);
    }
}
