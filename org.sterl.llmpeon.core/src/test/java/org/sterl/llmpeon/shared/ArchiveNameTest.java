package org.sterl.llmpeon.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class ArchiveNameTest {

    @Test
    void free_stem_returns_plain_md() {
        // GIVEN a stem whose .md file does not exist
        String stem = "overview-done-2026-09-05-12-59";
        // WHEN
        String result = ArchiveName.firstFreeName(stem, name -> false);
        // THEN the plain stem.md
        assertThat(result).isEqualTo(stem + ".md");
    }

    @Test
    void first_collision_appends_1() {
        // GIVEN stem.md already exists
        String stem = "overview-done-2026-09-05-12-59";
        Set<String> taken = Set.of(stem + ".md");
        // WHEN
        String result = ArchiveName.firstFreeName(stem, taken::contains);
        // THEN stem-1.md
        assertThat(result).isEqualTo(stem + "-1.md");
    }

    @Test
    void second_collision_appends_2() {
        // GIVEN stem.md and stem-1.md already exist
        String stem = "overview-done-2026-09-05-12-59";
        Set<String> taken = Set.of(stem + ".md", stem + "-1.md");
        // WHEN
        String result = ArchiveName.firstFreeName(stem, taken::contains);
        // THEN stem-2.md
        assertThat(result).isEqualTo(stem + "-2.md");
    }

    @Test
    void skips_to_first_free_name() {
        // GIVEN stem.md, stem-1.md, stem-2.md, stem-3.md all exist
        String stem = "overview-done-2026-09-05-12-59";
        Set<String> taken = Set.of(
                stem + ".md", stem + "-1.md", stem + "-2.md", stem + "-3.md");
        // WHEN
        String result = ArchiveName.firstFreeName(stem, taken::contains);
        // THEN stem-4.md (skips to the first free name)
        assertThat(result).isEqualTo(stem + "-4.md");
    }
}
