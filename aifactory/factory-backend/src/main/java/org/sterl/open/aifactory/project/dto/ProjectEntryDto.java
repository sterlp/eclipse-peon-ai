package org.sterl.open.aifactory.project.dto;

/**
 * One entry of a directory listing — a name, never a path.
 *
 * <p>A symlink is a name and nothing else: its target is never stat-ed, so {@code directory} is false
 * and {@code size} is 0 even when the target is a regular file that has one. {@code symlink} is what
 * tells a viewer "not followed" from "empty file" — a size of 0 alone would be a guess either way.</p>
 */
public record ProjectEntryDto(String name, boolean directory, long size, boolean symlink) {
}
