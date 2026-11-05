package org.sterl.open.aifactory.project.dto;

import java.util.List;

/**
 * One directory of a read root. {@code path} is the request's path <em>after</em> the guard resolved it
 * against that root — {@code ./} and {@code ..} are gone, the root itself is empty, and it is never a
 * real path, which is the whole point of the area being read-only (R-WP-14 / UC-WP-42). Two spellings
 * of the same directory therefore answer with the same {@code path}.
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
