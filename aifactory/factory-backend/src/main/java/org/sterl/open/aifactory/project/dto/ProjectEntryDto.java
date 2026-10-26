package org.sterl.open.aifactory.project.dto;

/** One entry of a directory listing — a name, never a path. */
public record ProjectEntryDto(String name, boolean directory, long size) {
}
