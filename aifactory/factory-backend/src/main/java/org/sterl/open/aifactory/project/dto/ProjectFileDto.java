package org.sterl.open.aifactory.project.dto;

/**
 * One file of a read root, at most {@code MAX_TEXT_BYTES} of it. {@code binary} replaces the content
 * with a marker instead of answering empty — an unreadable file and a file with nothing in it are
 * different facts for the caller.
 */
public record ProjectFileDto(String kind, String root, String path, String text, long size, boolean binary,
        boolean truncated) {

    public static final String KIND = "file";

    public static ProjectFileDto of(ProjectFileRoot root, String path, String text, long size, boolean binary,
            boolean truncated) {
        return new ProjectFileDto(KIND, root.wireName(), path, text, size, binary, truncated);
    }
}
